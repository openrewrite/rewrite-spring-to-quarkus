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
                    maybeRemoveImport("org.springframework.boot.autoconfigure.SpringBootApplication");
                }
                return super.visitAnnotation(annotation, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getSimpleName().equals("run") && method.getSelect() != null && method.getSelect().toString().contains("SpringApplication")) {
                    maybeRemoveImport("org.springframework.boot.SpringApplication");
                    return null;
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if ("main".equals(method.getSimpleName()) &&
                        method.getMethodType() != null &&
                        method.getMethodType().getParameterTypes().size() == 1 &&
                        method.getMethodType().getParameterTypes().get(0) instanceof JavaType.Array &&
                        TypeUtils.isOfType(((JavaType.Array)method.getMethodType().getParameterTypes().get(0)).getElemType(), JavaType.buildType("java.lang.String")) &&
                        method.hasModifier(J.Modifier.Type.Public) &&
                        method.hasModifier(J.Modifier.Type.Static)) {
                    if (method.getBody() != null && method.getBody().getStatements().size() == 1 && method.getBody().getStatements().get(0) instanceof J.MethodInvocation) {
                        J.MethodInvocation methodCall = (J.MethodInvocation) method.getBody().getStatements().get(0);
                        if (methodCall.getSimpleName().equals("run") && methodCall.getSelect() != null && methodCall.getSelect().toString().contains("SpringApplication")) {
                            maybeRemoveImport("org.springframework.boot.SpringApplication");
                            return null;
                        }
                    }
                }
                return super.visitMethodDeclaration(method, ctx);
            }
        };
    }
}
