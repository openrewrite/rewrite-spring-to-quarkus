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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringHealthIndicatorToQuarkusTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringHealthIndicatorToQuarkus())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-boot-actuator", "spring-context"))
          .typeValidationOptions(org.openrewrite.test.TypeValidation.none());
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
              import org.springframework.boot.actuate.health.HealthCheckResponse;
              import org.springframework.boot.actuate.health.HealthCheck;

              public class CustomHealthIndicator implements HealthCheck {
                  @Override
                  public HealthCheckResponse call() {
                      return HealthCheckResponse.up().build();
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

              public class DatabaseHealthIndicator implements HealthIndicator {
                  @Override
                  public Health health() {
                      return Health.up()
                              .withDetail("database", "PostgreSQL")
                              .build();
                  }
              }
              """,
            """
              import org.springframework.boot.actuate.health.HealthCheckResponse;
              import org.springframework.boot.actuate.health.HealthCheck;

              public class DatabaseHealthIndicator implements HealthCheck {
                  @Override
                  public HealthCheckResponse call() {
                      return HealthCheckResponse.up()
                              .withData("database", "PostgreSQL")
                              .build();
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
