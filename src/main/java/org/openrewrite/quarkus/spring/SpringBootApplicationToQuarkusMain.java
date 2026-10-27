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
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringBootApplicationToQuarkusMain extends Recipe {

    private static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String QUARKUS_MAIN = "io.quarkus.runtime.annotations.QuarkusMain";
    private static final String APPLICATION_SCOPED = "jakarta.enterprise.context.ApplicationScoped";

    private static final AnnotationMatcher SPRING_BOOT_APPLICATION_MATCHER = new AnnotationMatcher("@" + SPRING_BOOT_APPLICATION);
    private static final AnnotationMatcher PRODUCER = new AnnotationMatcher("@org.springframework.context.annotation.Bean");
    private static final AnnotationMatcher CDI_PRODUCER = new AnnotationMatcher("@jakarta.enterprise.inject.Produces");
    private static final AnnotationMatcher CONFIGURATION = new AnnotationMatcher("@org.springframework.context.annotation.Configuration");
    private static final AnnotationMatcher CDI_APPLICATION_SCOPED = new AnnotationMatcher("@" + APPLICATION_SCOPED);

    String displayName = "Replace `@SpringBootApplication` with `@QuarkusMain`";

    String description = "Replace `@SpringBootApplication` with `@QuarkusMain` on the class that declares the `main` method, " +
            "adding `@ApplicationScoped` when it declares bean producers, as `@QuarkusMain` is not bean defining. " +
            "On other classes, such as nested test applications, `@SpringBootApplication` becomes `@ApplicationScoped` when " +
            "the class declares producers and is removed otherwise, as Quarkus allows only one `@QuarkusMain` per application.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(SPRING_BOOT_APPLICATION, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                J.Annotation springBootApplication = cd.getLeadingAnnotations().stream()
                        .filter(SPRING_BOOT_APPLICATION_MATCHER::matches)
                        .findFirst()
                        .orElse(null);
                if (springBootApplication == null) {
                    return cd;
                }
                maybeRemoveImport(SPRING_BOOT_APPLICATION);
                boolean main = declaresMain(cd);
                boolean producers = cd.getBody().getStatements().stream().anyMatch(statement -> statement instanceof J.MethodDeclaration &&
                        ((J.MethodDeclaration) statement).getLeadingAnnotations().stream().anyMatch(a -> PRODUCER.matches(a) || CDI_PRODUCER.matches(a)));
                boolean addScope = producers && cd.getLeadingAnnotations().stream().noneMatch(a -> CONFIGURATION.matches(a) || CDI_APPLICATION_SCOPED.matches(a));
                if (!main && !addScope) {
                    return (J.ClassDeclaration) new RemoveAnnotationVisitor(SPRING_BOOT_APPLICATION_MATCHER).visitNonNull(cd, ctx, getCursor().getParentOrThrow());
                }
                List<J.Annotation> replacement = new ArrayList<>(2);
                if (main) {
                    maybeAddImport(QUARKUS_MAIN);
                    J.Annotation quarkusMain = replaceWith(springBootApplication, QUARKUS_MAIN);
                    String dropped = springBootApplication.getArguments() == null ? "" : springBootApplication.getArguments().stream()
                            .filter(arg -> !(arg instanceof J.Empty) &&
                                           !(arg instanceof J.Assignment && "proxyBeanMethods".equals(((J.Assignment) arg).getVariable().printTrimmed(getCursor()))))
                            .map(arg -> "`" + arg.printTrimmed(getCursor()) + "`")
                            .collect(joining(", "));
                    replacement.add(dropped.isEmpty() ? quarkusMain : SearchResult.found(quarkusMain, "`@QuarkusMain` has no equivalent for " + dropped));
                }
                if (addScope) {
                    maybeAddImport(APPLICATION_SCOPED);
                    J.Annotation applicationScoped = replaceWith(springBootApplication, APPLICATION_SCOPED);
                    replacement.add(main ? applicationScoped.withPrefix(Space.format("\n" + cd.getPrefix().getIndent())) : applicationScoped);
                }
                return cd.withLeadingAnnotations(ListUtils.flatMap(cd.getLeadingAnnotations(), a -> a == springBootApplication ? replacement : a));
            }

            private boolean declaresMain(J.ClassDeclaration cd) {
                for (Statement statement : cd.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration && "main".equals(((J.MethodDeclaration) statement).getSimpleName())) {
                        return true;
                    }
                    if (statement instanceof J.ClassDeclaration &&
                        ((J.ClassDeclaration) statement).getModifiers().stream().anyMatch(m -> "companion".equals(m.getKeyword())) &&
                        declaresMain((J.ClassDeclaration) statement)) {
                        return true;
                    }
                }
                // Kotlin declares `main` as a top-level function next to the application class
                Object parent = getCursor().getParentTreeCursor().getValue();
                return parent instanceof JavaSourceFile && new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
                        return classDecl;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, AtomicBoolean found) {
                        if ("main".equals(method.getSimpleName())) {
                            found.set(true);
                        }
                        return method;
                    }
                }.reduce((JavaSourceFile) parent, new AtomicBoolean()).get();
            }

            private J.Annotation replaceWith(J.Annotation annotation, String fullyQualifiedName) {
                JavaType.FullyQualified type = JavaType.ShallowClass.build(fullyQualifiedName);
                return annotation
                        .withAnnotationType(new J.Identifier(randomId(), annotation.getAnnotationType().getPrefix(), Markers.EMPTY,
                                emptyList(), type.getClassName(), type, null))
                        .withArguments(null);
            }
        });
    }
}
