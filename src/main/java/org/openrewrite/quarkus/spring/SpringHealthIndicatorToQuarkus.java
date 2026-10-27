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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringHealthIndicatorToQuarkus extends Recipe {

    private static final String HEALTH_INDICATOR = "org.springframework.boot.actuate.health.HealthIndicator";
    private static final String HEALTH = "org.springframework.boot.actuate.health.Health";
    private static final String HEALTH_BUILDER = HEALTH + "$Builder";
    private static final String COMPONENT = "org.springframework.stereotype.Component";

    private static final String HEALTH_CHECK = "org.eclipse.microprofile.health.HealthCheck";
    private static final String HEALTH_CHECK_RESPONSE = "org.eclipse.microprofile.health.HealthCheckResponse";
    private static final String HEALTH_CHECK_RESPONSE_BUILDER = "org.eclipse.microprofile.health.HealthCheckResponseBuilder";
    private static final String LIVENESS = "org.eclipse.microprofile.health.Liveness";
    private static final String READINESS = "org.eclipse.microprofile.health.Readiness";
    private static final String STARTUP = "org.eclipse.microprofile.health.Startup";
    private static final String APPLICATION_SCOPED = "jakarta.enterprise.context.ApplicationScoped";

    private static final MethodMatcher HEALTH_METHOD = new MethodMatcher(HEALTH_INDICATOR + " health()", true);
    private static final MethodMatcher STATIC_UP = new MethodMatcher(HEALTH + " up()");
    private static final MethodMatcher STATIC_DOWN = new MethodMatcher(HEALTH + " down()");
    private static final MethodMatcher NEW_BUILDER = new MethodMatcher(HEALTH_BUILDER + " <constructor>()");
    private static final MethodMatcher BUILDER_UP = new MethodMatcher(HEALTH_BUILDER + " up()");
    private static final MethodMatcher BUILDER_DOWN = new MethodMatcher(HEALTH_BUILDER + " down()");
    private static final MethodMatcher BUILDER_WITH_DETAIL = new MethodMatcher(HEALTH_BUILDER + " withDetail(String, Object)");
    private static final MethodMatcher BUILDER_BUILD = new MethodMatcher(HEALTH_BUILDER + " build()");
    private static final MethodMatcher RESPONSE_NAMED = new MethodMatcher(HEALTH_CHECK_RESPONSE + " named(String)");
    private static final MethodMatcher RESPONSE_BUILDER_UP = new MethodMatcher(HEALTH_CHECK_RESPONSE_BUILDER + " up()");
    private static final MethodMatcher RESPONSE_BUILDER_DOWN = new MethodMatcher(HEALTH_CHECK_RESPONSE_BUILDER + " down()");

    private static final Set<String> LONG_COMPATIBLE_TYPES = new HashSet<>(Arrays.asList(
            "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long"));

    private static final String HEALTH_CHECK_NAME = "HEALTH_CHECK_NAME";

    String displayName = "Convert Spring HealthIndicator to Quarkus HealthCheck";

    String description = "Transforms Spring Boot Actuator `HealthIndicator` implementations to MicroProfile Health `HealthCheck` pattern used by Quarkus.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(HEALTH_INDICATOR, false),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        if (!implementsHealthIndicator(classDecl)) {
                            return super.visitClassDeclaration(classDecl, ctx);
                        }

                        getCursor().putMessage(HEALTH_CHECK_NAME, healthCheckName(classDecl));
                        J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);

                        doAfterVisit(new ChangeType(HEALTH_INDICATOR, HEALTH_CHECK, true).getVisitor());
                        doAfterVisit(new ChangeType(HEALTH, HEALTH_CHECK_RESPONSE, true).getVisitor());

                        if (!hasAnnotation(cd, LIVENESS) && !hasAnnotation(cd, READINESS) && !hasAnnotation(cd, STARTUP)) {
                            maybeAddImport(LIVENESS);
                            cd = JavaTemplate.builder("@Liveness")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "microprofile-health-api-4"))
                                    .imports(LIVENESS)
                                    .build()
                                    .apply(updateCursor(cd), cd.getCoordinates().addAnnotation((a1, a2) -> 0));
                        }

                        // A Spring @Component is replaced by @ApplicationScoped later in the Spring Boot to Quarkus migration
                        if (!hasAnnotation(cd, APPLICATION_SCOPED) && !hasAnnotation(cd, COMPONENT)) {
                            maybeAddImport(APPLICATION_SCOPED);
                            cd = JavaTemplate.builder("@ApplicationScoped")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "jakarta.enterprise.cdi-api-4"))
                                    .imports(APPLICATION_SCOPED)
                                    .build()
                                    .apply(updateCursor(cd), cd.getCoordinates().addAnnotation((a1, a2) -> 0));
                        }

                        return cd;
                    }

                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                        if (getCursor().getNearestMessage(HEALTH_CHECK_NAME) != null && HEALTH_METHOD.matches(m.getMethodType())) {
                            JavaType.Method call = m.getMethodType().withName("call");
                            m = m.withName(m.getName().withSimpleName("call").withType(call)).withMethodType(call);
                        }
                        return m;
                    }

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J n = super.visitNewClass(newClass, ctx);
                        String name = getCursor().getNearestMessage(HEALTH_CHECK_NAME);
                        if (name != null && NEW_BUILDER.matches(newClass)) {
                            return healthCheckResponse("HealthCheckResponse.named(\"" + name + "\")", n, ctx);
                        }
                        return n;
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J j = super.visitMethodInvocation(method, ctx);
                        String name = getCursor().getNearestMessage(HEALTH_CHECK_NAME);
                        if (name == null || !(j instanceof J.MethodInvocation)) {
                            return j;
                        }
                        J.MethodInvocation m = (J.MethodInvocation) j;

                        if (STATIC_UP.matches(m)) {
                            return healthCheckResponse("HealthCheckResponse.named(\"" + name + "\").up()", m, ctx);
                        }
                        if (STATIC_DOWN.matches(m)) {
                            return healthCheckResponse("HealthCheckResponse.named(\"" + name + "\").down()", m, ctx);
                        }

                        Expression builder = m.getSelect();
                        if (builder == null || !TypeUtils.isOfClassType(builder.getType(), HEALTH_CHECK_RESPONSE_BUILDER)) {
                            return m;
                        }
                        if (BUILDER_BUILD.matches(m) && (RESPONSE_BUILDER_UP.matches(builder) || RESPONSE_BUILDER_DOWN.matches(builder))) {
                            J.MethodInvocation upOrDown = (J.MethodInvocation) builder;
                            if (RESPONSE_NAMED.matches(upOrDown.getSelect())) {
                                return healthCheckResponse("HealthCheckResponse." + upOrDown.getSimpleName() + "(#{any(java.lang.String)})",
                                        m, ctx, ((J.MethodInvocation) upOrDown.getSelect()).getArguments().get(0));
                            }
                        }

                        J.MethodInvocation retargeted;
                        if (BUILDER_UP.matches(m) || BUILDER_DOWN.matches(m) || BUILDER_BUILD.matches(m)) {
                            retargeted = (J.MethodInvocation) healthCheckResponse(
                                    "#{any(" + HEALTH_CHECK_RESPONSE_BUILDER + ")}." + m.getSimpleName() + "()", m, ctx, builder);
                        } else if (BUILDER_WITH_DETAIL.matches(m)) {
                            Expression value = m.getArguments().get(1);
                            retargeted = (J.MethodInvocation) healthCheckResponse(
                                    "#{any(" + HEALTH_CHECK_RESPONSE_BUILDER + ")}.withData(#{any(java.lang.String)}, " + dataValue(value.getType()) + ")",
                                    m, ctx, builder, m.getArguments().get(0), value);
                        } else {
                            return m;
                        }
                        // Keep the original formatting of the call, which matters for multi-line builder chains
                        return m.withName(retargeted.getName().withPrefix(m.getName().getPrefix()))
                                .withMethodType(retargeted.getMethodType())
                                .withArguments(ListUtils.map(retargeted.getArguments(), (i, arg) -> arg.withPrefix(m.getArguments().get(i).getPrefix())));
                    }

                    private J healthCheckResponse(String code, J tree, ExecutionContext ctx, Object... parameters) {
                        maybeAddImport(HEALTH_CHECK_RESPONSE);
                        return JavaTemplate.builder(code)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "microprofile-health-api-4"))
                                .imports(HEALTH_CHECK_RESPONSE)
                                .build()
                                .apply(updateCursor(tree), ((Expression) tree).getCoordinates().replace(), parameters);
                    }
                }
        );
    }

    private static boolean implementsHealthIndicator(J.ClassDeclaration classDecl) {
        if (classDecl.getImplements() != null) {
            for (TypeTree impl : classDecl.getImplements()) {
                if (TypeUtils.isOfClassType(impl.getType(), HEALTH_INDICATOR)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnnotation(J.ClassDeclaration classDecl, String annotationType) {
        for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
            if (TypeUtils.isOfClassType(annotation.getType(), annotationType)) {
                return true;
            }
        }
        return false;
    }

    // Spring Boot names a health indicator after its bean name minus the `HealthIndicator` suffix
    private static String healthCheckName(J.ClassDeclaration classDecl) {
        String name = classDecl.getSimpleName();
        if (name.endsWith("HealthIndicator") && name.length() > "HealthIndicator".length()) {
            name = name.substring(0, name.length() - "HealthIndicator".length());
        }
        return StringUtils.uncapitalize(name);
    }

    // `withData` only accepts `String`, `long` and `boolean` values, whereas Spring's `withDetail` accepts any object
    private static String dataValue(@Nullable JavaType type) {
        if (TypeUtils.isString(type)) {
            return "#{any(java.lang.String)}";
        }
        if (type == JavaType.Primitive.Boolean || TypeUtils.isOfClassType(type, "java.lang.Boolean")) {
            return "#{any(boolean)}";
        }
        if (LONG_COMPATIBLE_TYPES.contains(TypeUtils.toString(type))) {
            return "#{any(long)}";
        }
        return "String.valueOf(#{any()})";
    }
}
