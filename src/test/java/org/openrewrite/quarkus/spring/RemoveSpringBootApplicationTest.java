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
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveSpringBootApplicationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.RemoveSpringBootApplication")
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-boot-autoconfigure", "spring-beans", "spring-context", "spring-data-jpa", "spring-web")
            //language=java
            .dependsOn(
              """
                package org.springframework.boot;
                public class SpringApplication {
                    public static org.springframework.context.ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
                        return null;
                    }
                }
                """,
              """
                package org.springframework.boot.autoconfigure;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                @Documented
                public @interface SpringBootApplication {
                }
                """,
              """
                package org.springframework.context;
                public interface ConfigurableApplicationContext {
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void removeSpringBootApplicationAndMainMethod() {
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

              public class DemoApplication {
              }
              """
          )
        );
    }

    @Test
    void removeOnlySpringBootApplication() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;

              @SpringBootApplication
              public class DemoApplication {

                  public void someOtherMethod() {
                      System.out.println("Hello");
                  }
              }
              """,
            """
              public class DemoApplication {

                  public void someOtherMethod() {
                      System.out.println("Hello");
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

              class DemoApplication {
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
