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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.trait.Literal;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringBeanToCdiProduces extends Recipe {

    public static final String BEAN_FQN = "org.springframework.context.annotation.Bean";
    private static final Annotated.Matcher BEAN_MATCHER = new Annotated.Matcher("@" + BEAN_FQN);
    public static final String SCOPE_FQN = "org.springframework.context.annotation.Scope";
    private static final Annotated.Matcher SCOPE_MATCHER = new Annotated.Matcher("@" + SCOPE_FQN);

    public static final String PRODUCES_FQN = "jakarta.enterprise.inject.Produces";
    public static final String APPLICATION_SCOPED_FQN = "jakarta.enterprise.context.ApplicationScoped";
    public static final String CONFIGURABLE_BEAN_FACTORY_FQN = "org.springframework.beans.factory.config.ConfigurableBeanFactory";
    public static final String DEPENDENT_FQN = "jakarta.enterprise.context.Dependent";
    public static final String NAMED_FQN = "jakarta.inject.Named";
    public static final String DEPENDENT = "@Dependent";
    public static final String APPLICATION_SCOPED = "@ApplicationScoped";
    public static final String NAMED = "@Named";

    String displayName = "Replace Spring `@Bean` with CDI `@Produces`";

    String description = "Transform Spring `@Bean` methods to CDI `@Produces` methods with appropriate scope annotations.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(BEAN_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                        Optional<Annotated> annotated = BEAN_MATCHER.lower(getCursor()).findFirst();
                        if (!annotated.isPresent()) {
                            return m;
                        }
                        Annotated beanAnnotation = annotated.get();

                        maybeRemoveImport(BEAN_FQN);
                        maybeRemoveImport(SCOPE_FQN);
                        maybeRemoveImport(CONFIGURABLE_BEAN_FACTORY_FQN);
                        maybeAddImport(PRODUCES_FQN);
                        maybeAddImport(APPLICATION_SCOPED_FQN);
                        maybeAddImport(NAMED_FQN);
                        maybeAddImport(DEPENDENT_FQN);

                        String template = createTemplate(
                                beanAnnotation.getDefaultAttribute("name").map(Literal::getString).orElse(null),
                                determineCdiScope(SCOPE_MATCHER.lower(getCursor()).findFirst().map(Annotated::getTree).orElse(null)));
                        return JavaTemplate.builder(template)
                                .imports(PRODUCES_FQN, APPLICATION_SCOPED_FQN, DEPENDENT_FQN, NAMED_FQN)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.enterprise.cdi-api", "jakarta.inject-api"))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replaceAnnotations());
                    }

                    private String createTemplate(@Nullable String beanName, @Nullable String scopeToAdd) {
                        StringBuilder templateBuilder = new StringBuilder("@Produces\n");
                        if (beanName != null) {
                            templateBuilder.append(NAMED + "(\"").append(beanName).append("\")\n");
                        }
                        if (scopeToAdd != null) {
                            templateBuilder.append(scopeToAdd);
                        } else {
                            templateBuilder.append(APPLICATION_SCOPED);
                        }
                        return templateBuilder.toString();
                    }

                    private @Nullable String determineCdiScope(J.@Nullable Annotation scopeAnnotation) {
                        if (scopeAnnotation == null || scopeAnnotation.getArguments() == null || scopeAnnotation.getArguments().isEmpty()) {
                            return null;
                        }

                        Expression arg = scopeAnnotation.getArguments().get(0);
                        String scopeValue = null;
                        if (arg instanceof J.Literal) {
                            scopeValue = (String) ((J.Literal) arg).getValue();
                        } else if (arg instanceof J.FieldAccess) {
                            scopeValue = ((J.FieldAccess) arg).getSimpleName();
                        }

                        return (scopeValue != null && scopeValue.toLowerCase().contains("prototype")) ? DEPENDENT : APPLICATION_SCOPED;
                    }
                });
    }
}
