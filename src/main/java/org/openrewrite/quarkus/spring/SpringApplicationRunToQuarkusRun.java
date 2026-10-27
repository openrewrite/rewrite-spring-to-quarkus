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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.EnumSet;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringApplicationRunToQuarkusRun extends Recipe {

    private static final MethodMatcher SPRING_APPLICATION_RUN = new MethodMatcher("org.springframework.boot.SpringApplication run(..)", true);
    private static final MethodMatcher SPRING_APPLICATION_BUILDER_RUN = new MethodMatcher("org.springframework.boot.builder.SpringApplicationBuilder run(..)", true);
    private static final MethodMatcher KOTLIN_RUN_APPLICATION = new MethodMatcher("org.springframework.boot.SpringApplicationExtensionsKt runApplication(..)");

    private static final JavaType.FullyQualified QUARKUS = JavaType.ShallowClass.build("io.quarkus.runtime.Quarkus");
    private static final JavaType.Method QUARKUS_RUN = new JavaType.Method(null, Flag.flagsToBitMap(EnumSet.of(Flag.Public, Flag.Static)),
            QUARKUS, "run", JavaType.Primitive.Void, singletonList("args"), singletonList(new JavaType.Array(null, JavaType.Primitive.String, null)),
            emptyList(), emptyList(), null, emptyList());

    String displayName = "Replace `SpringApplication.run()` with `Quarkus.run()`";

    String description = "Replace Spring Boot's `SpringApplication.run()`, `new SpringApplicationBuilder(Application.class).run()` " +
            "and Kotlin `runApplication<Application>()` calls with Quarkus's `Quarkus.run()`. Calls whose result is used, or that " +
            "customize the `SpringApplication` first, are marked for manual migration instead, as `Quarkus.run()` returns `void`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesMethod<>(SPRING_APPLICATION_RUN),
                        new UsesMethod<>(SPRING_APPLICATION_BUILDER_RUN),
                        new UsesMethod<>(KOTLIN_RUN_APPLICATION)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!SPRING_APPLICATION_RUN.matches(mi) && !SPRING_APPLICATION_BUILDER_RUN.matches(mi) && !KOTLIN_RUN_APPLICATION.matches(mi)) {
                            return mi;
                        }
                        List<Expression> args = applicationArguments(mi);
                        if (args == null) {
                            return SearchResult.found(mi, "Move the `SpringApplication` customization to Quarkus configuration, then call `Quarkus.run(args)`");
                        }
                        if (!isStandaloneStatement()) {
                            return SearchResult.found(mi, "`Quarkus.run(args)` returns `void`, so the use of the application context has to be migrated manually");
                        }
                        maybeRemoveImport("org.springframework.boot.SpringApplication");
                        maybeRemoveImport("org.springframework.boot.builder.SpringApplicationBuilder");
                        maybeRemoveImport("org.springframework.boot.runApplication");
                        maybeAddImport("io.quarkus.runtime.Quarkus");
                        Space selectPrefix = mi.getSelect() == null ? Space.EMPTY : mi.getSelect().getPrefix();
                        return mi.withSelect(new J.Identifier(randomId(), selectPrefix, Markers.EMPTY, emptyList(), "Quarkus", QUARKUS, null))
                                .withName(mi.getName().withSimpleName("run").withType(QUARKUS_RUN))
                                .withMethodType(QUARKUS_RUN)
                                .getPadding().withTypeParameters(null)
                                .withArguments(ListUtils.mapFirst(args, arg -> arg.withPrefix(Space.EMPTY)));
                    }

                    private @Nullable List<Expression> applicationArguments(J.MethodInvocation mi) {
                        List<Expression> args = mi.getArguments();
                        if (KOTLIN_RUN_APPLICATION.matches(mi)) {
                            return args.get(args.size() - 1) instanceof J.Lambda ? null : args;
                        }
                        if (mi.getSelect() instanceof J.NewClass) {
                            J.NewClass newClass = (J.NewClass) mi.getSelect();
                            List<Expression> constructorArgs = newClass.getArguments();
                            return newClass.getBody() == null && constructorArgs.size() == 1 && isPrimarySource(constructorArgs.get(0)) ? args : null;
                        }
                        if (mi.getMethodType() != null && mi.getMethodType().hasFlags(Flag.Static) && isPrimarySource(args.get(0))) {
                            return ListUtils.mapFirst(args, arg -> null);
                        }
                        return null;
                    }

                    private boolean isPrimarySource(Expression expression) {
                        return TypeUtils.isOfClassType(expression.getType(), "java.lang.Class");
                    }

                    private boolean isStandaloneStatement() {
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        if (parent instanceof J.Block) {
                            return true;
                        }
                        // A Kotlin `fun main(args: Array<String>) = runApplication<App>(*args)` still compiles once it returns `Unit`
                        J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        return parent instanceof J.Return && enclosing != null && "main".equals(enclosing.getSimpleName());
                    }
                }
        );
    }
}
