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

class SpringWebToJaxRsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringWebToJaxRs())
          .parser(JavaParser.fromJavaVersion().classpath("spring-web"));
    }

    @DocumentExample
    @Test
    void convertRestControllerToPath() {
        rewriteRun(
          java(
            """
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              @RequestMapping("/api")
              public class MyController {

                  @GetMapping("/hello")
                  public String hello() {
                      return "Hello, World!";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.Path;
              import jakarta.ws.rs.Produces;
              import jakarta.ws.rs.core.MediaType;

              @Path("/api")
              public class MyController {

                  @GET
                  @Path("/hello")
                  @Produces(MediaType.TEXT_PLAIN)
                  public String hello() {
                      return "Hello, World!";
                  }
              }
              """
          )
        );
    }
}
