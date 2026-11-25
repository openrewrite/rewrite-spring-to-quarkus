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

class MigrateSpringActuatorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.MigrateSpringActuator")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "micrometer-core-1", "micrometer-registry-prometheus-1"));
    }

    @DocumentExample
    @Test
    void movePrometheusMeterRegistryToPrometheusClient1Package() {
        rewriteRun(
          //language=java
          java(
            """
              import io.micrometer.prometheus.PrometheusMeterRegistry;

              class Metrics {
                  String scrape(PrometheusMeterRegistry registry) {
                      return registry.scrape();
                  }
              }
              """,
            """
              import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

              class Metrics {
                  String scrape(PrometheusMeterRegistry registry) {
                      return registry.scrape();
                  }
              }
              """
          )
        );
    }
}
