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
import static org.openrewrite.maven.Assertions.pomXml;

class ReplaceSpringBootApplicationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.ReplaceSpringBootApplication")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-3",
            "spring-boot-autoconfigure-3",
            "spring-context-6",
            "spring-data-jpa-3",
            "spring-web-6"))
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-3",
            "spring-boot-autoconfigure-3",
            "spring-context-6"));
    }

    @DocumentExample
    @Test
    void replaceSpringBootApplicationAndUpdateMainMethod() {
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
              import io.quarkus.runtime.annotations.QuarkusMain;

              @QuarkusMain
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
    void migrateBasicSpringBootApplication() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              class DemoApplication {

                  public static void main(String[] args) {
                      SpringApplication.run(DemoApplication.class, args);
                  }
              }
              """,
            """
              package com.example;

              import io.quarkus.runtime.Quarkus;
              import io.quarkus.runtime.annotations.QuarkusMain;

              @QuarkusMain
              class DemoApplication {

                  public static void main(String[] args) {
                      Quarkus.run(args);
                  }
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.quarkus.platform</groupId>
                              <artifactId>quarkus-bom</artifactId>
                              <version>3.26.4</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          )
        );
    }

    @Test
    void dropAttributesQuarkusMainDoesNotHave() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.web.bind.annotation.RestController;

              @SpringBootApplication(scanBasePackages = "com.example.multimodule", proxyBeanMethods = false)
              @RestController
              public class DemoApplication {
                  public static void main(String[] args) {
                      SpringApplication.run(DemoApplication.class, args);
                  }
              }
              """,
            """
              import io.quarkus.runtime.Quarkus;
              import io.quarkus.runtime.annotations.QuarkusMain;
              import org.springframework.web.bind.annotation.RestController;

              /*~~(`@QuarkusMain` has no equivalent for `scanBasePackages = "com.example.multimodule"`)~~>*/@QuarkusMain
              @RestController
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
    void addApplicationScopedToMainClassWithProducers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.context.annotation.Bean;

              import java.util.concurrent.Executor;
              import java.util.concurrent.Executors;

              @SpringBootApplication
              public class AsyncMethodApplication {
                  public static void main(String[] args) {
                      SpringApplication.run(AsyncMethodApplication.class, args);
                  }

                  @Bean
                  public Executor taskExecutor() {
                      return Executors.newFixedThreadPool(2);
                  }
              }
              """,
            """
              import io.quarkus.runtime.Quarkus;
              import io.quarkus.runtime.annotations.QuarkusMain;
              import jakarta.enterprise.context.ApplicationScoped;
              import org.springframework.context.annotation.Bean;

              import java.util.concurrent.Executor;
              import java.util.concurrent.Executors;

              @QuarkusMain
              @ApplicationScoped
              public class AsyncMethodApplication {
                  public static void main(String[] args) {
                      Quarkus.run(args);
                  }

                  @Bean
                  public Executor taskExecutor() {
                      return Executors.newFixedThreadPool(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceOnKotlinClassWithTopLevelMain() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication
              import org.springframework.boot.runApplication
              import org.springframework.context.annotation.Bean
              import java.util.concurrent.Executor
              import java.util.concurrent.Executors

              @SpringBootApplication
              class AsyncMethodApplication {

                  @Bean
                  fun taskExecutor(): Executor = Executors.newFixedThreadPool(2)
              }

              fun main(args: Array<String>) {
                  runApplication<AsyncMethodApplication>(*args)
              }
              """,
            """
              import io.quarkus.runtime.Quarkus
              import io.quarkus.runtime.annotations.QuarkusMain
              import jakarta.enterprise.context.ApplicationScoped
              import org.springframework.context.annotation.Bean
              import java.util.concurrent.Executor
              import java.util.concurrent.Executors

              @QuarkusMain
              @ApplicationScoped
              class AsyncMethodApplication {

                  @Bean
                  fun taskExecutor(): Executor = Executors.newFixedThreadPool(2)
              }

              fun main(args: Array<String>) {
                  Quarkus.run(*args)
              }
              """
          )
        );
    }

    @Test
    void replaceOnKotlinClassWithCompanionMain() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication
              import org.springframework.boot.runApplication

              @SpringBootApplication
              class Application {
                  companion object {
                      @JvmStatic
                      fun main(args: Array<String>) = runApplication<Application>(*args)
                  }
              }
              """,
            """
              import io.quarkus.runtime.Quarkus
              import io.quarkus.runtime.annotations.QuarkusMain

              @QuarkusMain
              class Application {
                  companion object {
                      @JvmStatic
                      fun main(args: Array<String>) = Quarkus.run(*args)
                  }
              }
              """
          )
        );
    }

    @Test
    void applicationScopedOnNestedTestApplicationWithProducers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.context.annotation.Bean;

              class SpringDocApp1Test {
                  @SpringBootApplication
                  static class TestApp {
                      @Bean
                      String testGroupedOpenApi() {
                          return "test-group";
                      }
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import org.springframework.context.annotation.Bean;

              class SpringDocApp1Test {
                  @ApplicationScoped
                  static class TestApp {
                      @Bean
                      String testGroupedOpenApi() {
                          return "test-group";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFromClassWithoutMainOrProducers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

              /**
               * @author Christoph Strobl
               */
              @SpringBootApplication
              @EnableJpaRepositories
              class PagingRepoConfig {}
              """,
            """
              import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

              /**
               * @author Christoph Strobl
               */
              @EnableJpaRepositories
              class PagingRepoConfig {}
              """
          )
        );
    }

    @Test
    void doNotChangeNonSpringBootApplication() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;
              class RegularApplication {
                  public static void main(String[] args) {
                  }
              }
              """
          )
        );
    }
}
