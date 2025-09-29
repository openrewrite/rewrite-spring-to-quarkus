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
import org.openrewrite.java.Assertions;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class SpringBootApplicationMigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.SpringBootApplicationMigration")
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-boot-autoconfigure", "spring-beans", "spring-context", "spring-data-jpa", "spring-web"));
    }

    @DocumentExample
    @Test
    void migrateBasicSpringBootApplication() {
        rewriteRun(
          //language=java
          java(
            """
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
    void migrateSpringBootApplicationWithEnableScheduling() {
        rewriteRun(
          //language=java
          java(
            """
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

    @Test
    void addSpringCompatibilityExtensionsForCommonAnnotations() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=java
              java(
                """
                      import org.springframework.stereotype.Service;
                  import org.springframework.web.bind.annotation.RestController;
                  import org.springframework.web.bind.annotation.GetMapping;

                  @RestController
                  class DemoController {

                      @GetMapping("/hello")
                      public String hello() {
                          return "Hello World";
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                      import org.springframework.stereotype.Service;
                  import org.springframework.beans.factory.annotation.Autowired;
                  import org.springframework.data.repository.Repository;

                  @Service
                  class DemoService {

                      @Autowired
                      private Repository<?,?> repository;

                      public void doSomething() {
                          // business logic
                      }
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
                            <artifactId>quarkus-spring-di</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-spring-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void doNotChangeNonSpringBootApplication() {
        rewriteRun(
          //language=java
          java(
            """
              class RegularApplication {

                  public static void main(String[] args) {
                      System.out.println("Hello World");
                  }
              }
              """
          )
        );
    }
}
