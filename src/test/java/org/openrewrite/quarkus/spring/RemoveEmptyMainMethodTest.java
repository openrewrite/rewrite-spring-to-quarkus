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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveEmptyMainMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveEmptyMainMethod());
    }

    @DocumentExample
    @Test
    void removeEmptyMainMethod() {
        rewriteRun(
          java(
            """
              public class Application {
                  public static void main(String[] args) {
                      // This method will be removed
                  }
              }
              """,
            """
              public class Application {
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "public static void main(String[] args) {}",
      "public static void main(String[] args) {    }",
      "public static void main(String[] args) { /* empty */ }",
      "public static void main(String[] args) { ; }",
      "public static void main(String... args) { ; }",
    })
    void removeEmptyMainMethod(String methodSignature) {
        rewriteRun(
          java(
            """
              public class Application {
                  %s
              }
              """.formatted(methodSignature),
            """
              public class Application {
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "static void main(String[] args) {}",
      "public void main(String[] args) {}",
      "public static int main(String[] args) { return 0; }",
      "public static void main2(String[] args) {}",
      "public static void main() {}",
      "public static void main(String[] args) { System.out.println(args[0]); }"
    })
    void noChange(String methodSignature) {
        rewriteRun(
          java(
            """
              public class Application {
                  %s
              }
              """.formatted(methodSignature)
          )
        );
    }
}
