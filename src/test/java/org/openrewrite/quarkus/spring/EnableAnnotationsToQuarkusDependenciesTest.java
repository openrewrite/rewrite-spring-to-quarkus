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

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class EnableAnnotationsToQuarkusDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources(
            "org.openrewrite.quarkus.spring.RemoveSpringBootApplication",
            "org.openrewrite.quarkus.spring.EnableAnnotationsToQuarkusDependencies")
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-boot-autoconfigure", "spring-beans", "spring-context", "spring-data-jpa", "spring-web")
            //language=java
            .dependsOn(
              """
                package org.springframework.scheduling.annotation;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                @Documented
                public @interface EnableScheduling {
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void convertEnableSchedulingToQuarkusScheduler() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.scheduling.annotation.EnableScheduling;

                  @EnableScheduling
                  class SchedulingConfig {
                  }
                  """,
                """
                  class SchedulingConfig {
                  }
                  """
              )
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
                """,
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
                    <dependencies>
                        <dependency>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-scheduler</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void migrateSpringBootApplicationWithEnableScheduling() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.scheduling.annotation.EnableScheduling;

              @SpringBootApplication
              @EnableScheduling
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
                  <dependencies>
                      <dependency>
                          <groupId>io.quarkus</groupId>
                          <artifactId>quarkus-scheduler</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void migrateSpringBootApplicationWithEnableCaching() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.cache.annotation.EnableCaching;

              @SpringBootApplication
              @EnableCaching
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
                  <dependencies>
                      <dependency>
                          <groupId>io.quarkus</groupId>
                          <artifactId>quarkus-cache</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void migrateSpringBootApplicationWithEnableJpaRepositories() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

              @SpringBootApplication
              @EnableJpaRepositories
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
                  <dependencies>
                      <dependency>
                          <groupId>io.quarkus</groupId>
                          <artifactId>quarkus-spring-data-jpa</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void migrateSpringBootApplicationWithMultipleEnableAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import org.springframework.boot.SpringApplication;
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.scheduling.annotation.EnableScheduling;
              import org.springframework.cache.annotation.EnableCaching;
              import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

              @SpringBootApplication
              @EnableScheduling
              @EnableCaching
              @EnableJpaRepositories
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
                  <dependencies>
                      <dependency>
                          <groupId>io.quarkus</groupId>
                          <artifactId>quarkus-scheduler</artifactId>
                      </dependency>
                      <dependency>
                          <groupId>io.quarkus</groupId>
                          <artifactId>quarkus-cache</artifactId>
                      </dependency>
                      <dependency>
                          <groupId>io.quarkus</groupId>
                          <artifactId>quarkus-spring-data-jpa</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }
}
