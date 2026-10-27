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
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class SpringApplicationRunToQuarkusRunTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringApplicationRunToQuarkusRun())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-3",
            "spring-boot-autoconfigure-3",
            "spring-beans-6",
            "spring-context-6",
            "spring-core-6"))
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-3",
            "spring-boot-autoconfigure-3",
            "spring-beans-6",
            "spring-context-6",
            "spring-core-6"));
    }

    @DocumentExample
    @Test
    void replaceSpringApplicationRun() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class DemoApplication {
                  public static void main(String[] args) {
                      SpringApplication.run(DemoApplication.class, args);
                  }
              }
              """,
            """
              package com.example;

              import io.quarkus.runtime.Quarkus;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class DemoApplication {
                  public static void main(String[] args) {
                      Quarkus.run(args);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceVarargs() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class DemoApplication {
                  public static void main(String[] args) {
                      SpringApplication.run(DemoApplication.class, "one", "two", "three");
                  }
              }
              """,
            """
              package com.example;

              import io.quarkus.runtime.Quarkus;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class DemoApplication {
                  public static void main(String[] args) {
                      Quarkus.run("one", "two", "three");
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWithOtherPrimarySources() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.builder.SpringApplicationBuilder;

              public class ConfigApplication {
                  public static void main(String[] args) {
                      new SpringApplicationBuilder(ConfigApplication.class).run(args);
                  }
              }
              """,
            """
              import io.quarkus.runtime.Quarkus;

              public class ConfigApplication {
                  public static void main(String[] args) {
                      Quarkus.run(args);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.boot.SpringApplication;

              public class IntegrationApplication {
                  public static void main(String[] args) {
                      new SpringApplication(IntegrationApplication.class).run(args);
                  }
              }
              """,
            """
              import io.quarkus.runtime.Quarkus;

              public class IntegrationApplication {
                  public static void main(String[] args) {
                      Quarkus.run(args);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.boot.SpringApplication;

              public class MultiSourceApplication {
                  public static void main(String[] args) {
                      SpringApplication.run(new Class<?>[]{MultiSourceApplication.class, Object.class}, args);
                  }
              }
              """,
            """
              import io.quarkus.runtime.Quarkus;

              public class MultiSourceApplication {
                  public static void main(String[] args) {
                      Quarkus.run(args);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceInKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication
              import org.springframework.boot.runApplication

              @SpringBootApplication
              class RestServiceApplication

              fun main(args: Array<String>) {
                runApplication<RestServiceApplication>(*args)
              }
              """,
            """
              import io.quarkus.runtime.Quarkus
              import org.springframework.boot.autoconfigure.SpringBootApplication

              @SpringBootApplication
              class RestServiceApplication

              fun main(args: Array<String>) {
                Quarkus.run(*args)
              }
              """
          ),
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.SpringApplication

              class DataJdbcH2Application

              fun main(args: Array<String>) {
              	SpringApplication.run(DataJdbcH2Application::class.java, *args)
              }
              """,
            """
              import io.quarkus.runtime.Quarkus

              class DataJdbcH2Application

              fun main(args: Array<String>) {
              	Quarkus.run(*args)
              }
              """
          )
        );
    }

    @Test
    void markWhenApplicationContextIsUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.SpringApplication;
              import org.springframework.context.ConfigurableApplicationContext;

              public class Application {
                  public static void main(String[] args) {
                      ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);
                      context.getBean(Object.class);
                  }
              }
              """,
            """
              import org.springframework.boot.SpringApplication;
              import org.springframework.context.ConfigurableApplicationContext;

              public class Application {
                  public static void main(String[] args) {
                      ConfigurableApplicationContext context = /*~~(`Quarkus.run(args)` returns `void`, so the use of the application context has to be migrated manually)~~>*/SpringApplication.run(Application.class, args);
                      context.getBean(Object.class);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.boot.SpringApplication;

              public class BatchProcessingApplication {
                  public static void main(String[] args) {
                      System.exit(SpringApplication.exit(SpringApplication.run(BatchProcessingApplication.class, args)));
                  }
              }
              """,
            """
              import org.springframework.boot.SpringApplication;

              public class BatchProcessingApplication {
                  public static void main(String[] args) {
                      System.exit(SpringApplication.exit(/*~~(`Quarkus.run(args)` returns `void`, so the use of the application context has to be migrated manually)~~>*/SpringApplication.run(BatchProcessingApplication.class, args)));
                  }
              }
              """
          ),
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.runApplication

              class AsyncMethodApplication

              fun main(args: Array<String>) {
                  // close the application context to shut down the custom ExecutorService
                  runApplication<AsyncMethodApplication>(*args).close()
              }
              """,
            """
              import org.springframework.boot.runApplication

              class AsyncMethodApplication

              fun main(args: Array<String>) {
                  // close the application context to shut down the custom ExecutorService
                  /*~~(`Quarkus.run(args)` returns `void`, so the use of the application context has to be migrated manually)~~>*/runApplication<AsyncMethodApplication>(*args).close()
              }
              """
          )
        );
    }

    @Test
    void markCustomizedSpringApplication() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.WebApplicationType;

              public class WebClientApplication {
                  public static void main(String[] args) {
                      SpringApplication application = new SpringApplication(WebClientApplication.class);
                      application.setWebApplicationType(WebApplicationType.NONE);
                      application.run(args);
                  }
              }
              """,
            """
              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.WebApplicationType;

              public class WebClientApplication {
                  public static void main(String[] args) {
                      SpringApplication application = new SpringApplication(WebClientApplication.class);
                      application.setWebApplicationType(WebApplicationType.NONE);
                      /*~~(Move the `SpringApplication` customization to Quarkus configuration, then call `Quarkus.run(args)`)~~>*/application.run(args);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.boot.WebApplicationType;
              import org.springframework.boot.builder.SpringApplicationBuilder;

              public class CustomerServiceApplication {
                  public static void main(String[] args) {
                      new SpringApplicationBuilder(CustomerServiceApplication.class)
                          .web(WebApplicationType.NONE)
                          .run(args);
                  }
              }
              """,
            """
              import org.springframework.boot.WebApplicationType;
              import org.springframework.boot.builder.SpringApplicationBuilder;

              public class CustomerServiceApplication {
                  public static void main(String[] args) {
                      /*~~(Move the `SpringApplication` customization to Quarkus configuration, then call `Quarkus.run(args)`)~~>*/new SpringApplicationBuilder(CustomerServiceApplication.class)
                          .web(WebApplicationType.NONE)
                          .run(args);
                  }
              }
              """
          ),
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.Banner
              import org.springframework.boot.runApplication

              class MyApplication

              fun main(args: Array<String>) {
                  runApplication<MyApplication>(*args) {
                      setBannerMode(Banner.Mode.OFF)
                  }
              }
              """,
            """
              import org.springframework.boot.Banner
              import org.springframework.boot.runApplication

              class MyApplication

              fun main(args: Array<String>) {
                  /*~~(Move the `SpringApplication` customization to Quarkus configuration, then call `Quarkus.run(args)`)~~>*/runApplication<MyApplication>(*args) {
                      setBannerMode(Banner.Mode.OFF)
                  }
              }
              """
          )
        );
    }
}
