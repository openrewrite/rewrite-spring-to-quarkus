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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.SearchResult;

import static org.openrewrite.quarkus.spring.SpringBeanToCdiProduces.annotation;
import static org.openrewrite.quarkus.spring.SpringBeanToCdiProduces.unconvertibleConfigurationReason;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringConfigurationToCdi extends Recipe {

    private static final String CONFIGURATION_FQN = "org.springframework.context.annotation.Configuration";
    private static final AnnotationMatcher CONFIGURATION_MATCHER = new AnnotationMatcher("@" + CONFIGURATION_FQN);

    String displayName = "Replace Spring `@Configuration` with a CDI scope";

    String description = "Replace `@Configuration` with `@ApplicationScoped`, or with `@Singleton` when CDI cannot create a " +
            "client proxy for the class because it is final or has no non-private no-arg constructor. Conditional and " +
            "Spring Security configuration classes are left unchanged and marked for manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(CONFIGURATION_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        J.Annotation configuration = null;
                        for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                            if (CONFIGURATION_MATCHER.matches(annotation)) {
                                configuration = annotation;
                            }
                        }
                        if (configuration == null) {
                            return cd;
                        }
                        J.Annotation replaced = configuration;
                        String reason = unconvertibleConfigurationReason(cd);
                        if (reason != null) {
                            return cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), a -> a == replaced ? SearchResult.found(a, reason) : a));
                        }

                        boolean hasConstructor = false;
                        boolean hasNoArgConstructor = false;
                        for (Statement statement : cd.getBody().getStatements()) {
                            if (statement instanceof J.MethodDeclaration && ((J.MethodDeclaration) statement).isConstructor()) {
                                J.MethodDeclaration constructor = (J.MethodDeclaration) statement;
                                hasConstructor = true;
                                hasNoArgConstructor |= constructor.getParameters().stream().allMatch(J.Empty.class::isInstance) &&
                                                       !constructor.hasModifier(J.Modifier.Type.Private);
                            }
                        }
                        String scope = cd.hasModifier(J.Modifier.Type.Final) || hasConstructor && !hasNoArgConstructor ?
                                "jakarta.inject.Singleton" : SpringBeanToCdiProduces.APPLICATION_SCOPED_FQN;
                        maybeRemoveImport(CONFIGURATION_FQN);
                        maybeAddImport(scope);
                        return cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), a -> a == replaced ? annotation(scope, null, a.getPrefix()) : a));
                    }
                });
    }
}
