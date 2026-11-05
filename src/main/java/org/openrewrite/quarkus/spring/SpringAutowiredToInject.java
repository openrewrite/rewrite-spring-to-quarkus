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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.quarkus.spring.SpringBeanToCdiProduces.annotation;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringAutowiredToInject extends Recipe {

    private static final String AUTOWIRED_FQN = "org.springframework.beans.factory.annotation.Autowired";
    private static final String INJECT_FQN = "jakarta.inject.Inject";
    private static final AnnotationMatcher AUTOWIRED_MATCHER = new AnnotationMatcher("@" + AUTOWIRED_FQN);
    private static final AnnotationMatcher OPTIONAL_AUTOWIRED_MATCHER = new AnnotationMatcher("@" + AUTOWIRED_FQN + "(required = false)");
    private static final List<AnnotationMatcher> INJECTING_METHOD_ANNOTATIONS = Stream.of(
                    AUTOWIRED_FQN, INJECT_FQN, SpringBeanToCdiProduces.BEAN_FQN, SpringBeanToCdiProduces.PRODUCES_FQN)
            .map(fqn -> new AnnotationMatcher("@" + fqn))
            .collect(toList());

    String displayName = "Replace Spring `@Autowired` with CDI `@Inject`";

    String description = "Replace `@Autowired` on fields, constructors and methods with `@Inject`, and remove it from " +
            "parameters of constructors and of injection or producer methods, which CDI injects anyway. Optional " +
            "injection (`required = false`) and other method parameters, such as those of test methods, are left " +
            "unchanged and marked for manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(AUTOWIRED_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        AnnotationService annotationService = service(AnnotationService.class);
                        Cursor method = getCursor().getParentTreeCursor();
                        if (annotationService.matches(getCursor(), AUTOWIRED_MATCHER) &&
                            !annotationService.matches(getCursor(), OPTIONAL_AUTOWIRED_MATCHER) &&
                            method.getValue() instanceof J.MethodDeclaration &&
                            (((J.MethodDeclaration) method.getValue()).isConstructor() ||
                             INJECTING_METHOD_ANNOTATIONS.stream().anyMatch(matcher -> annotationService.matches(method, matcher)))) {
                            maybeRemoveImport(AUTOWIRED_FQN);
                            return (J.VariableDeclarations) new RemoveAnnotationVisitor(AUTOWIRED_MATCHER)
                                    .visitNonNull(multiVariable, ctx, getCursor().getParentOrThrow());
                        }
                        return super.visitVariableDeclarations(multiVariable, ctx);
                    }

                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        if (!AUTOWIRED_MATCHER.matches(a)) {
                            return a;
                        }
                        if (OPTIONAL_AUTOWIRED_MATCHER.matches(a)) {
                            return SearchResult.found(a, "Optional injection; migrate to `@Inject Instance<T>` manually");
                        }
                        Cursor annotated = getCursor().getParentTreeCursor();
                        if (annotated.getValue() instanceof J.VariableDeclarations &&
                            annotated.getParentTreeCursor().getValue() instanceof J.MethodDeclaration) {
                            return SearchResult.found(a, "CDI does not inject method parameters; inject an `@Inject` field instead");
                        }
                        maybeRemoveImport(AUTOWIRED_FQN);
                        maybeAddImport(INJECT_FQN);
                        return annotation(INJECT_FQN, null, a.getPrefix());
                    }
                });
    }
}
