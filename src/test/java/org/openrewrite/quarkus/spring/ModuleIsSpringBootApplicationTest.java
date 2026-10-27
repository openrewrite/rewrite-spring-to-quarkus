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

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;

class ModuleIsSpringBootApplicationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ModuleIsSpringBootApplication())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-3", "spring-boot-autoconfigure-3", "spring-context-6"));
    }

    @DocumentExample
    @Test
    void markApplicationModule() {
        rewriteRun(
          mavenProject("demo",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.4</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              """
                <!--~~(Spring Boot application module)~~>--><project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.4</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
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
                  /*~~(Spring Boot application module)~~>*/import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class DemoApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(DemoApplication.class, args);
                      }
                  }
                  """
              )
            ),
            srcMainResources(
              properties(
                "server.port=8081",
                "~~(Spring Boot application module)~~>server.port=8081",
                spec -> spec.path("application.properties")
              )
            )
          )
        );
    }

    @Test
    void markApplicationOnSpringBootMilestone() {
        rewriteRun(
          mavenProject("demo",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>4.1.0-M4</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              """
                <!--~~(Spring Boot application module)~~>--><project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>4.1.0-M4</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
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
                  /*~~(Spring Boot application module)~~>*/import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class DemoApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(DemoApplication.class, args);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doNotMarkSpringBoot2Application() {
        rewriteRun(
          mavenProject("demo",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>2.7.18</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class DemoApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(DemoApplication.class, args);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doNotMarkLibraryWithTestApplication() {
        rewriteRun(
          mavenProject("library",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>library</artifactId>
                    <version>1.0.0</version>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.context.annotation.Configuration;

                  @Configuration
                  public class LibraryAutoConfiguration {
                  }
                  """
              )
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class TestApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(TestApplication.class, args);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doNotMarkLibraryWithoutBootPlugin() {
        rewriteRun(
          mavenProject("library",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>library</artifactId>
                    <version>1.0.0</version>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class GenericApplicationMain {
                      public static void main(String[] args) {
                          SpringApplication.run(GenericApplicationMain.class, args);
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doNotMarkConfigurationWithoutMainMethod() {
        rewriteRun(
          mavenProject("library",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>library</artifactId>
                    <version>1.0.0</version>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.boot.SpringBootConfiguration;

                  @SpringBootConfiguration
                  public class LibraryConfiguration {
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void markParentAndModulesTheApplicationDependsOn() {
        rewriteRun(
          mavenProject("parent",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>app</module>
                        <module>core</module>
                        <module>tools</module>
                    </modules>
                </project>
                """,
              """
                <!--~~(Spring Boot application module)~~>--><project>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>app</module>
                        <module>core</module>
                        <module>tools</module>
                    </modules>
                </project>
                """
            )
          ),
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>app</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.4</version>
                        </dependency>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>core</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              """
                <!--~~(Spring Boot application module)~~>--><project>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>app</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.4</version>
                        </dependency>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>core</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
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
                  /*~~(Spring Boot application module)~~>*/import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class DemoApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(DemoApplication.class, args);
                      }
                  }
                  """
              )
            )
          ),
          mavenProject("core",
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>core</artifactId>
                </project>
                """,
              """
                <!--~~(Spring Boot application module)~~>--><project>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>core</artifactId>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.stereotype.Service;

                  @Service
                  public class GreetingService {
                  }
                  """,
                """
                  /*~~(Spring Boot application module)~~>*/import org.springframework.stereotype.Service;

                  @Service
                  public class GreetingService {
                  }
                  """
              )
            )
          ),
          mavenProject("tools",
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>tools</artifactId>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.stereotype.Component;

                  @Component
                  public class Tool {
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doNotMarkProjectsNestedInResources() {
        rewriteRun(
          mavenProject("demo",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.4</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
              """
                <!--~~(Spring Boot application module)~~>--><project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.5.4</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
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
                  /*~~(Spring Boot application module)~~>*/import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class DemoApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(DemoApplication.class, args);
                      }
                  }
                  """
              )
            ),
            srcMainResources(
              properties(
                "server.port=8081",
                "~~(Spring Boot application module)~~>server.port=8081",
                spec -> spec.path("config/application.properties")
              ),
              text(
                "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.example.Foo",
                spec -> spec.path("META-INF/spring.factories")
              )
            ),
            srcTestResources(
              //language=java
              java(
                """
                  import org.springframework.boot.SpringApplication;
                  import org.springframework.boot.autoconfigure.SpringBootApplication;

                  @SpringBootApplication
                  public class FixtureApplication {
                      public static void main(String[] args) {
                          SpringApplication.run(FixtureApplication.class, args);
                      }
                  }
                  """,
                spec -> spec.path("projects/fixture/src/main/java/FixtureApplication.java")
              ),
              properties(
                "server.port=8082",
                spec -> spec.path("projects/fixture/src/main/resources/application.properties")
              )
            )
          )
        );
    }
}
