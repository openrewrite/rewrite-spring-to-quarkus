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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringHealthIndicatorToQuarkus extends Recipe {

    private static final String HEALTH_INDICATOR_FQN = "org.springframework.boot.actuate.health.HealthIndicator";
    private static final String SPRING_HEALTH_FQN = "org.springframework.boot.actuate.health.Health";
    private static final String QUARKUS_HEALTH_CHECK_FQN = "org.eclipse.microprofile.health.HealthCheck";
    private static final String QUARKUS_HEALTH_RESPONSE_FQN = "org.eclipse.microprofile.health.HealthCheckResponse";
    private static final String LIVENESS_FQN = "org.eclipse.microprofile.health.Liveness";
    private static final String APPLICATION_SCOPED_FQN = "jakarta.enterprise.context.ApplicationScoped";

    private static final MethodMatcher HEALTH_METHOD = new MethodMatcher("org.springframework.boot.actuate.health.HealthIndicator health()");
    private static final MethodMatcher HEALTH_UP = new MethodMatcher("org.springframework.boot.actuate.health.Health$Builder up()");
    private static final MethodMatcher HEALTH_DOWN = new MethodMatcher("org.springframework.boot.actuate.health.Health$Builder down()");
    private static final MethodMatcher HEALTH_BUILD = new MethodMatcher("org.springframework.boot.actuate.health.Health$Builder build()");
    private static final MethodMatcher WITH_DETAIL = new MethodMatcher("org.springframework.boot.actuate.health.Health$Builder withDetail(..)");

    String displayName = "Convert Spring HealthIndicator to Quarkus HealthCheck";

    String description = "Transforms Spring Boot Actuator `HealthIndicator` implementations to MicroProfile Health `HealthCheck` pattern used by Quarkus.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(HEALTH_INDICATOR_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        // Check if class implements HealthIndicator
                        if (cd.getImplements() == null) {
                            return cd;
                        }

                        boolean implementsHealthIndicator = false;
                        for (TypeTree impl : cd.getImplements()) {
                            if (impl.getType() != null &&
                                    HEALTH_INDICATOR_FQN.equals(impl.getType().toString())) {
                                implementsHealthIndicator = true;
                                break;
                            }
                        }

                        if (!implementsHealthIndicator) {
                            return cd;
                        }

                        // Add imports
                        maybeRemoveImport(HEALTH_INDICATOR_FQN);
                        maybeRemoveImport(SPRING_HEALTH_FQN);
                        maybeAddImport(QUARKUS_HEALTH_CHECK_FQN);
                        maybeAddImport(QUARKUS_HEALTH_RESPONSE_FQN);
                        maybeAddImport(LIVENESS_FQN);
                        maybeAddImport(APPLICATION_SCOPED_FQN);

                        // Add @Liveness annotation
                        boolean hasLiveness = cd.getLeadingAnnotations().stream()
                                .anyMatch(ann -> "Liveness".equals(ann.getSimpleName()) ||
                                        "Readiness".equals(ann.getSimpleName()));
                        if (!hasLiveness) {
                            cd = JavaTemplate.builder("@Liveness")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "microprofile-health-api"))
                                    .imports(LIVENESS_FQN)
                                    .build()
                                    .apply(getCursor(), cd.getCoordinates().addAnnotation((a1, a2) -> 0));
                        }

                        // Add @ApplicationScoped if not present
                        boolean hasApplicationScoped = cd.getLeadingAnnotations().stream()
                                .anyMatch(ann -> "ApplicationScoped".equals(ann.getSimpleName()));
                        if (!hasApplicationScoped) {
                            cd = JavaTemplate.builder("@ApplicationScoped")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "jakarta.enterprise.cdi-api"))
                                    .imports(APPLICATION_SCOPED_FQN)
                                    .build()
                                    .apply(updateCursor(cd), cd.getCoordinates().addAnnotation((a1, a2) -> 0));
                        }

                        return cd;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier ident, ExecutionContext ctx) {
                        J.Identifier id = super.visitIdentifier(ident, ctx);

                        // Change HealthIndicator to HealthCheck in implements clause
                        if ("HealthIndicator".equals(id.getSimpleName()) &&
                                id.getType() != null &&
                                HEALTH_INDICATOR_FQN.equals(id.getType().toString())) {
                            return id.withSimpleName("HealthCheck")
                                    .withType(JavaType.buildType(QUARKUS_HEALTH_CHECK_FQN));
                        }

                        // Change Health to HealthCheckResponse in return types
                        if ("Health".equals(id.getSimpleName()) &&
                                id.getType() != null &&
                                SPRING_HEALTH_FQN.equals(id.getType().toString())) {
                            return id.withSimpleName("HealthCheckResponse")
                                    .withType(JavaType.buildType(QUARKUS_HEALTH_RESPONSE_FQN));
                        }

                        return id;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                        // Rename health() to call()
                        if ("health".equals(m.getSimpleName())) {
                            m = m.withName(m.getName().withSimpleName("call"));
                        }

                        return m;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                        // Health.up() -> HealthCheckResponse.up("name")
                        if (HEALTH_UP.matches(m)) {
                            return JavaTemplate.builder("HealthCheckResponse.up(\"health\")")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "microprofile-health-api"))
                                    .imports(QUARKUS_HEALTH_RESPONSE_FQN)
                                    .build()
                                    .apply(getCursor(), m.getCoordinates().replace());
                        }

                        // Health.down() -> HealthCheckResponse.down("name")
                        if (HEALTH_DOWN.matches(m)) {
                            return JavaTemplate.builder("HealthCheckResponse.down(\"health\")")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "microprofile-health-api"))
                                    .imports(QUARKUS_HEALTH_RESPONSE_FQN)
                                    .build()
                                    .apply(getCursor(), m.getCoordinates().replace());
                        }

                        // .build() -> .build() (same but ensure proper type)
                        if (HEALTH_BUILD.matches(m)) {
                            // Keep as is, the type will be updated through other transformations
                            return m;
                        }

                        // .withDetail(key, value) -> .withData(key, value)
                        if (WITH_DETAIL.matches(m)) {
                            return m.withName(m.getName().withSimpleName("withData"));
                        }

                        return m;
                    }
                }
        );
    }
}
