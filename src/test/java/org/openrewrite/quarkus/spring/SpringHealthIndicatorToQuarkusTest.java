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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringHealthIndicatorToQuarkusTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringHealthIndicatorToQuarkus())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "spring-boot-actuator-3", "spring-context-6"));
    }

    @DocumentExample
    @Test
    void convertSimpleHealthIndicator() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.actuate.health.Health;
              import org.springframework.boot.actuate.health.HealthIndicator;

              public class CustomHealthIndicator implements HealthIndicator {
                  @Override
                  public Health health() {
                      return Health.up().build();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import org.eclipse.microprofile.health.HealthCheck;
              import org.eclipse.microprofile.health.HealthCheckResponse;
              import org.eclipse.microprofile.health.Liveness;

              @Liveness
              @ApplicationScoped
              public class CustomHealthIndicator implements HealthCheck {
                  @Override
                  public HealthCheckResponse call() {
                      return HealthCheckResponse.up("custom");
                  }
              }
              """
          )
        );
    }

    @Test
    void convertWithDetailToWithData() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.actuate.health.Health;
              import org.springframework.boot.actuate.health.HealthIndicator;

              import java.time.Duration;

              public class DatabaseHealthIndicator implements HealthIndicator {
                  @Override
                  public Health health() {
                      return Health.up()
                              .withDetail("database", "PostgreSQL")
                              .withDetail("connections", 10)
                              .withDetail("readOnly", false)
                              .withDetail("latency", Duration.ofMillis(5))
                              .build();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import org.eclipse.microprofile.health.HealthCheck;
              import org.eclipse.microprofile.health.HealthCheckResponse;
              import org.eclipse.microprofile.health.Liveness;

              import java.time.Duration;

              @Liveness
              @ApplicationScoped
              public class DatabaseHealthIndicator implements HealthCheck {
                  @Override
                  public HealthCheckResponse call() {
                      return HealthCheckResponse.named("database").up()
                              .withData("database", "PostgreSQL")
                              .withData("connections", 10)
                              .withData("readOnly", false)
                              .withData("latency", String.valueOf(Duration.ofMillis(5)))
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void convertDownBuilderChain() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.actuate.health.Health;
              import org.springframework.boot.actuate.health.HealthIndicator;

              public class DiskSpaceHealthIndicator implements HealthIndicator {
                  private final boolean full;

                  DiskSpaceHealthIndicator(boolean full) {
                      this.full = full;
                  }

                  @Override
                  public Health health() {
                      if (full) {
                          return Health.down().build();
                      }
                      return new Health.Builder().up().withDetail("free", 42L).build();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import org.eclipse.microprofile.health.HealthCheck;
              import org.eclipse.microprofile.health.HealthCheckResponse;
              import org.eclipse.microprofile.health.Liveness;

              @Liveness
              @ApplicationScoped
              public class DiskSpaceHealthIndicator implements HealthCheck {
                  private final boolean full;

                  DiskSpaceHealthIndicator(boolean full) {
                      this.full = full;
                  }

                  @Override
                  public HealthCheckResponse call() {
                      if (full) {
                          return HealthCheckResponse.down("diskSpace");
                      }
                      return HealthCheckResponse.named("diskSpace").up().withData("free", 42L).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void addLivenessButLeaveComponentForStereotypeMigration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.actuate.health.Health;
              import org.springframework.boot.actuate.health.HealthIndicator;
              import org.springframework.stereotype.Component;

              @Component
              public class PingHealthIndicator implements HealthIndicator {
                  @Override
                  public Health health() {
                      return Health.up().build();
                  }
              }
              """,
            """
              import org.eclipse.microprofile.health.HealthCheck;
              import org.eclipse.microprofile.health.HealthCheckResponse;
              import org.eclipse.microprofile.health.Liveness;
              import org.springframework.stereotype.Component;

              @Component
              @Liveness
              public class PingHealthIndicator implements HealthCheck {
                  @Override
                  public HealthCheckResponse call() {
                      return HealthCheckResponse.up("ping");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonHealthIndicator() {
        rewriteRun(
          //language=java
          java(
            """
              public class RegularService {
                  public String health() {
                      return "healthy";
                  }
              }
              """
          )
        );
    }
}
