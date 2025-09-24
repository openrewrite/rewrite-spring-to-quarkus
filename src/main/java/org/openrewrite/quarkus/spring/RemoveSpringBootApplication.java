/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.quarkus.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveSpringBootApplication extends Recipe {

    private static final MethodMatcher SPRING_APPLICATION_RUN = new MethodMatcher("org.springframework.boot.SpringApplication run(..)");

    @Override
    public String getDisplayName() {
        return "Remove Spring Boot application class";
    }

    @Override
    public String getDescription() {
        return "Removes @SpringBootApplication annotation and SpringApplication.run() calls from Spring Boot main classes, optionally removing the entire class if it becomes empty.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                if (annotation.getSimpleName().equals("SpringBootApplication")) {
                    doAfterVisit(new RemoveAnnotation("org.springframework.boot.autoconfigure.SpringBootApplication").getVisitor());
                }
                return super.visitAnnotation(annotation, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (SPRING_APPLICATION_RUN.matches(method)) {
                    maybeRemoveImport("org.springframework.boot.SpringApplication");
                    return null;
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if ("main".equals(md.getSimpleName()) &&
                        md.getMethodType() != null &&
                        md.getMethodType().getParameterTypes().size() == 1 &&
                        md.getMethodType().getParameterTypes().get(0) instanceof JavaType.Array &&
                        TypeUtils.isOfType(((JavaType.Array)md.getMethodType().getParameterTypes().get(0)).getElemType(), JavaType.buildType("java.lang.String")) &&
                        md.hasModifier(J.Modifier.Type.Public) &&
                        md.hasModifier(J.Modifier.Type.Static)) {
                    if (md.getBody() != null && md.getBody().getStatements().isEmpty()) {
                        return null;
                    }
                }
                return md;
            }
        };
    }
}
