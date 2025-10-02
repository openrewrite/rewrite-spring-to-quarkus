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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringApplicationRunToQuarkusRun extends Recipe {

    private static final MethodMatcher SPRING_APPLICATION_RUN = new MethodMatcher("org.springframework.boot.SpringApplication run(..)", true);

    @Override
    public String getDisplayName() {
        return "Replace `SpringApplication.run()` with `Quarkus.run()`";
    }

    @Override
    public String getDescription() {
        return "Replace Spring Boot's `SpringApplication.run()` method calls with Quarkus's `Quarkus.run()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SPRING_APPLICATION_RUN), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (SPRING_APPLICATION_RUN.matches(mi)) {
                            maybeRemoveImport("org.springframework.boot.SpringApplication");
                            maybeAddImport("io.quarkus.runtime.Quarkus");
                            // SpringApplication.run(AppClass.class, args) -> Quarkus.run(args)
                            J.MethodInvocation quarkusRun = JavaTemplate.builder("Quarkus.run()")
                                    .imports("io.quarkus.runtime.Quarkus")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "quarkus-core-3"))
                                    .build()
                                    .apply(getCursor(), mi.getCoordinates().replace());
                            List<Expression> args = ListUtils.mapFirst(mi.getArguments(), arg -> null);
                            return quarkusRun.withArguments(ListUtils.mapFirst(args, arg -> arg.withPrefix(Space.EMPTY)));
                        }
                        return mi;
                    }
                }
        );
    }
}
