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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringValueToCdiConfigProperty extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace Spring @Value with CDI @ConfigProperty";
    }

    @Override
    public String getDescription() {
        return "Transform Spring @Value annotations to MicroProfile @ConfigProperty with proper parameter mapping.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);

                // Replace @Value with @ConfigProperty (complex transformation)
                if (isSpringAnnotation(a, "Value") && a.getArguments() != null && !a.getArguments().isEmpty()) {
                    maybeRemoveImport("org.springframework.beans.factory.annotation.Value");
                    maybeAddImport("org.eclipse.microprofile.config.inject.ConfigProperty");
                    
                    // Extract the property key from @Value("${property.key:defaultValue}")
                    if (a.getArguments().get(0) instanceof J.Literal) {
                        J.Literal literal = (J.Literal) a.getArguments().get(0);
                        String value = (String) literal.getValue();
                        if (value != null && value.startsWith("${") && value.endsWith("}")) {
                            String propertyExpression = value.substring(2, value.length() - 1);
                            String propertyKey;
                            String defaultValue = null;
                            
                            if (propertyExpression.contains(":")) {
                                String[] parts = propertyExpression.split(":", 2);
                                propertyKey = parts[0];
                                defaultValue = parts[1];
                            } else {
                                propertyKey = propertyExpression;
                            }
                            
                            String configPropertyTemplate = defaultValue != null 
                                ? String.format("@ConfigProperty(name = \"%s\", defaultValue = \"%s\")", propertyKey, defaultValue)
                                : String.format("@ConfigProperty(name = \"%s\")", propertyKey);
                            
                            return JavaTemplate.builder(configPropertyTemplate)
                                    .imports("org.eclipse.microprofile.config.inject.ConfigProperty")
                                    .javaParser(JavaParser.fromJavaVersion().classpath("microprofile-config-api"))
                                    .build()
                                    .apply(getCursor(), a.getCoordinates().replace());
                        }
                    }
                }

                return a;
            }

            private boolean isSpringAnnotation(J.Annotation annotation, String simpleName) {
                return annotation.getSimpleName().equals(simpleName);
            }
        };
    }
}