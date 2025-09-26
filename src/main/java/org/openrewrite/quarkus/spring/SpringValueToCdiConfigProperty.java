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
import org.openrewrite.java.tree.TypeUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringValueToCdiConfigProperty extends Recipe {

    /*
    The regex pattern [$][{]([^:}]+)(?::([^}]+))?[}] breaks down as:
    - [$][{] - matches literal ${
    - ([^:}]+) - captures property key (anything except : or })
    - (?::([^}]+))? - optionally captures :defaultValue
    - [}] - matches literal }
     */
    private static final Pattern VALUE_ANNOTATION_PROPERTY_VALUE = Pattern.compile("[$][{]([^:}]+)(?::([^}]+))?[}]");

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
                if (TypeUtils.isOfClassType(a.getType(), "org.springframework.beans.factory.annotation.Value") && a.getArguments() != null && !a.getArguments().isEmpty()) {
                    maybeRemoveImport("org.springframework.beans.factory.annotation.Value");
                    maybeAddImport("org.eclipse.microprofile.config.inject.ConfigProperty");

                    if (a.getArguments().get(0) instanceof J.Literal) {
                        J.Literal literal = (J.Literal) a.getArguments().get(0);
                        String value = (String) literal.getValue();
                        if (value != null) {
                            Matcher matcher = VALUE_ANNOTATION_PROPERTY_VALUE.matcher(value);
                            if (matcher.matches()) {
                                String propertyKey = matcher.group(1);
                                String defaultValue = matcher.group(2);

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
                }
                return a;
            }
        };
    }
}
