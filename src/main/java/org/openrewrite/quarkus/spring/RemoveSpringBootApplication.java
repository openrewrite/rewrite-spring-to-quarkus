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
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.tree.J;

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
                // Remove @SpringBootApplication annotation
                if (annotation.getSimpleName().equals("SpringBootApplication")) {
                    doAfterVisit(new RemoveAnnotation("org.springframework.boot.autoconfigure.SpringBootApplication").getVisitor());
                    maybeRemoveImport("org.springframework.boot.autoconfigure.SpringBootApplication");
                }
                return super.visitAnnotation(annotation, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                // Remove SpringApplication.run() calls
                if (method.getSimpleName().equals("run") && method.getSelect() != null && method.getSelect().toString().contains("SpringApplication")) {

                    maybeRemoveImport("org.springframework.boot.SpringApplication");

                    // Return null to remove the method invocation
                    return null;
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // Handle main method that only contains SpringApplication.run()
                if (method.getSimpleName().equals("main") && method.getModifiers().stream().anyMatch(mod -> mod instanceof J.Modifier && ((J.Modifier) mod).getType() == J.Modifier.Type.Static) && method.getModifiers().stream().anyMatch(mod -> mod instanceof J.Modifier && ((J.Modifier) mod).getType() == J.Modifier.Type.Public)) {

                    // Check if method body only contains SpringApplication.run()
                    if (method.getBody() != null && method.getBody().getStatements().size() == 1 && method.getBody().getStatements().get(0) instanceof J.MethodInvocation) {

                        J.MethodInvocation methodCall = (J.MethodInvocation) method.getBody().getStatements().get(0);
                        if (methodCall.getSimpleName().equals("run") && methodCall.getSelect() != null && methodCall.getSelect().toString().contains("SpringApplication")) {

                            maybeRemoveImport("org.springframework.boot.SpringApplication");

                            // Remove the entire main method as it's no longer needed
                            return null;
                        }
                    }
                }
                return super.visitMethodDeclaration(method, ctx);
            }
        };
    }
}
