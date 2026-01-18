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
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;

class MigrateSpringBootDevToolsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath()
          .build()
          .activateRecipes("org.openrewrite.quarkus.spring.MigrateSpringBootDevTools"));
    }

    @DocumentExample
    @Test
    void removeDevToolsProperties() {
        rewriteRun(
          properties(
            """
              server.port=8080
              spring.devtools.restart.enabled=true
              spring.devtools.livereload.enabled=true
              spring.application.name=myapp
              """,
            """
              server.port=8080
              spring.application.name=myapp
              """,
            spec -> spec.path("application.properties")
          )
        );
    }

    @Test
    void removeAllDevToolsProperties() {
        rewriteRun(
          properties(
            """
              spring.devtools.restart.enabled=true
              spring.devtools.livereload.enabled=false
              spring.devtools.restart.exclude=static/**
              spring.devtools.restart.additional-paths=src/main/resources
              spring.devtools.restart.additional-exclude=test/**
              """,
            "",
            spec -> spec.path("application.properties")
          )
        );
    }

    @Test
    void doNotChangeNonDevToolsProperties() {
        rewriteRun(
          properties(
            """
              server.port=8080
              spring.application.name=myapp
              logging.level.root=INFO
              """,
            spec -> spec.path("application.properties")
          )
        );
    }
}
