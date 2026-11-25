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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class SpringBootToQuarkusTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.SpringBootToQuarkus")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-3",
            "spring-boot-autoconfigure-3",
            "spring-web-6",
            "spring-context-6"));
    }

    @DocumentExample
    @Test
    void migrateSpringBootParentToQuarkusBOM() {
        rewriteRun(
          mavenProject("project",
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
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.1.0</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <properties>
                        <java.version>17</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
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
              spec -> spec.after(actual -> {
                  Matcher versionMatcher = Pattern.compile("<artifactId>quarkus-bom</artifactId>\\s*<version>(4\\.[^<]+)</version>").matcher(actual);
                  assertThat(versionMatcher.find()).isTrue();
                  String quarkusVersion = versionMatcher.group(1);
                  return """
                    <project>
                        <groupId>com.example</groupId>
                        <artifactId>demo</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                        <properties>
                            <maven.compiler.parameters>true</maven.compiler.parameters>
                            <maven.compiler.release>21</maven.compiler.release>
                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                            <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                        </properties>
                        <dependencyManagement>
                            <dependencies>
                                <dependency>
                                    <groupId>io.quarkus.platform</groupId>
                                    <artifactId>quarkus-bom</artifactId>
                                    <version>%s</version>
                                    <type>pom</type>
                                    <scope>import</scope>
                                </dependency>
                            </dependencies>
                        </dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.quarkus</groupId>
                                <artifactId>quarkus-resteasy-jackson</artifactId>
                            </dependency>
                        </dependencies>
                        <build>
                            <plugins>
                                <plugin>
                                    <groupId>io.quarkus.platform</groupId>
                                    <artifactId>quarkus-maven-plugin</artifactId>
                                    <version>%s</version>
                                    <executions>
                                        <execution>
                                            <goals>
                                                <goal>build</goal>
                                                <goal>generate-code</goal>
                                                <goal>generate-code-tests</goal>
                                            </goals>
                                        </execution>
                                    </executions>
                                    <extensions>true</extensions>
                                </plugin>
                            </plugins>
                        </build>
                    </project>
                    """.formatted(quarkusVersion, quarkusVersion);
              })
            )
          )
        );
    }

    @Test
    void migrateSpringBootWithExistingDependencyManagement() {
        rewriteRun(
          mavenProject("project",
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
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.1.0</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.fasterxml.jackson</groupId>
                                <artifactId>jackson-bom</artifactId>
                                <version>2.15.2</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
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
              spec -> spec.after(actual -> {
                  Matcher versionMatcher = Pattern.compile("<artifactId>quarkus-bom</artifactId>\\s*<version>(4\\.[^<]+)</version>").matcher(actual);
                  assertThat(versionMatcher.find()).isTrue();
                  String quarkusVersion = versionMatcher.group(1);
                  Matcher jacksonMatcher = Pattern.compile("<artifactId>jackson-bom</artifactId>\\s*<version>(3\\.[^<]+)</version>").matcher(actual);
                  assertThat(jacksonMatcher.find()).isTrue();
                  return """
                    <project>
                        <groupId>com.example</groupId>
                        <artifactId>demo</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                        <properties>
                            <maven.compiler.parameters>true</maven.compiler.parameters>
                            <maven.compiler.release>21</maven.compiler.release>
                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                            <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                        </properties>
                        <dependencyManagement>
                            <dependencies>
                                <dependency>
                                    <groupId>tools.jackson</groupId>
                                    <artifactId>jackson-bom</artifactId>
                                    <version>%s</version>
                                    <type>pom</type>
                                    <scope>import</scope>
                                </dependency>
                                <dependency>
                                    <groupId>io.quarkus.platform</groupId>
                                    <artifactId>quarkus-bom</artifactId>
                                    <version>%s</version>
                                    <type>pom</type>
                                    <scope>import</scope>
                                </dependency>
                            </dependencies>
                        </dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.quarkus</groupId>
                                <artifactId>quarkus-resteasy-jackson</artifactId>
                            </dependency>
                        </dependencies>
                        <build>
                            <plugins>
                                <plugin>
                                    <groupId>io.quarkus.platform</groupId>
                                    <artifactId>quarkus-maven-plugin</artifactId>
                                    <version>%s</version>
                                    <executions>
                                        <execution>
                                            <goals>
                                                <goal>build</goal>
                                                <goal>generate-code</goal>
                                                <goal>generate-code-tests</goal>
                                            </goals>
                                        </execution>
                                    </executions>
                                    <extensions>true</extensions>
                                </plugin>
                            </plugins>
                        </build>
                    </project>
                    """.formatted(jacksonMatcher.group(1), quarkusVersion, quarkusVersion);
              })
            )
          )
        );
    }

    @Test
    void migrateSpringBootDependencyAndWebEndpoints() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              //language=xml
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>3.1.0</version>
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
              spec -> spec.after(pom -> assertThat(pom)
                .contains(
                  "quarkus-bom",
                  "<artifactId>quarkus-resteasy-jackson</artifactId>",
                  "<artifactId>quarkus-spring-web</artifactId>",
                  "<artifactId>quarkus-maven-plugin</artifactId>")
                .doesNotContain("<artifactId>spring-boot-starter-web</artifactId>")
                .actual())
            ),
            srcMainJava(
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
                  import io.quarkus.runtime.Quarkus;
                  import io.quarkus.runtime.annotations.QuarkusMain;

                  @QuarkusMain
                  public class DemoApplication {
                      public static void main(String[] args) {
                          Quarkus.run(args);
                      }
                  }
                  """
              ),
              java(
                """
                  import org.springframework.web.bind.annotation.RestController;
                  import org.springframework.web.bind.annotation.GetMapping;

                  @RestController
                  public class UserController {

                      @GetMapping("/users")
                      public String getUsers() {
                          return "users";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;

                  @Path("")
                  public class UserController {

                      @GET
                      @Path("/users")
                      public String getUsers() {
                          return "users";
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void migrateSpringBoot4() {
        rewriteRun(
          mavenProject("project",
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
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.0</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
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
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>quarkus-bom</artifactId>", "<artifactId>quarkus-maven-plugin</artifactId>")
                .doesNotContain("<artifactId>spring-boot-starter-parent</artifactId>")
                .actual())
            )
          )
        );
    }

    @Test
    void doNotMigrateLibrary() {
        rewriteRun(
          mavenProject("library",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>library</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>3.1.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.GetMapping;
                  import org.springframework.web.bind.annotation.RestController;

                  @RestController
                  public class UserController {
                      @GetMapping("/users")
                      public String getUsers() {
                          return "users";
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void doNotMigrateProjectsNestedInResources() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                            <version>3.1.0</version>
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
              spec -> spec.after(pom -> assertThat(pom).contains("<artifactId>quarkus-bom</artifactId>").actual())
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
            ),
            srcTestResources(
              //language=java
              java(
                """
                  import org.springframework.stereotype.Service;

                  @Service
                  public class FixtureService {
                  }
                  """,
                spec -> spec.path("projects/fixture/src/main/java/FixtureService.java")
              )
            )
          )
        );
    }

    @Test
    void doNotMigrateSpringBoot2x() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.18</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
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
    void doNotTransformNonSpringBootProject() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
              </project>
              """
          )
        );
    }

    @Test
    void migrateJacksonToVersion3() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-3", "spring-boot-autoconfigure-3", "jackson-annotations-2", "jackson-core-2", "jackson-databind-2")),
          mavenProject("project",
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
                  import io.quarkus.runtime.Quarkus;
                  import io.quarkus.runtime.annotations.QuarkusMain;

                  @QuarkusMain
                  public class DemoApplication {
                      public static void main(String[] args) {
                          Quarkus.run(args);
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import com.fasterxml.jackson.annotation.JsonProperty;
                  import com.fasterxml.jackson.core.JsonProcessingException;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Greeting {
                      @JsonProperty("text")
                      String message;

                      String toJson(ObjectMapper mapper) throws JsonProcessingException {
                          return mapper.writeValueAsString(this);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonProperty;
                  import tools.jackson.databind.ObjectMapper;

                  class Greeting {
                      @JsonProperty("text")
                      String message;

                      String toJson(ObjectMapper mapper) {
                          return mapper.writeValueAsString(this);
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.1.0</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
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
              spec -> spec.after(pom -> assertThat(pom)
                .contains("<artifactId>quarkus-resteasy-jackson</artifactId>")
                .containsPattern("<groupId>tools.jackson.core</groupId>\\s*<artifactId>jackson-databind</artifactId>\\s*</dependency>")
                .doesNotContain("com.fasterxml.jackson")
                .actual())
            )
          )
        );
    }

    @Test
    void migrateGradleBuild() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("project",
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
            ),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.5.4'
                    id 'io.spring.dependency-management' version '1.1.7'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                """,
              spec -> spec.after(actual -> {
                  Matcher versionMatcher = Pattern.compile("id 'io\\.quarkus' version '(4\\.[^']+)'").matcher(actual);
                  assertThat(versionMatcher.find()).isTrue();
                  String quarkusVersion = versionMatcher.group(1);
                  return """
                    plugins {
                        id 'java'
                        id 'io.quarkus' version '%s'
                    }
                    repositories {
                        mavenCentral()
                    }
                    dependencies {
                        implementation platform("io.quarkus.platform:quarkus-bom:%s")
                        implementation "io.quarkus:quarkus-resteasy-jackson"

                        testImplementation "io.quarkus:quarkus-junit"
                    }
                    """.formatted(quarkusVersion, quarkusVersion);
              })
            )
          )
        );
    }
}
