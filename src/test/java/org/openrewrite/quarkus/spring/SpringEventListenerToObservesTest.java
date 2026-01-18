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

class SpringEventListenerToObservesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringEventListenerToObserves())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-context"));
    }

    @DocumentExample
    @Test
    void convertEventListenerToObserves() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.event.EventListener;

              public class MyEventListener {
                  @EventListener
                  public void handleEvent(MyEvent event) {
                      System.out.println("Event received: " + event);
                  }
              }
              """,
            """
              import jakarta.enterprise.event.Observes;

              public class MyEventListener {
                  public void handleEvent(@Observes MyEvent event) {
                      System.out.println("Event received: " + event);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              public class MyEvent {
                  private String message;
                  public String getMessage() { return message; }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodWithoutEventListener() {
        rewriteRun(
          //language=java
          java(
            """
              public class RegularService {
                  public void handleEvent(String event) {
                      System.out.println(event);
                  }
              }
              """
          )
        );
    }
}
