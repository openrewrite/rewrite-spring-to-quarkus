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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class SpringBootToQuarkusTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.SpringBootToQuarkus");
    }

    @DocumentExample
    @Test
    void migrateSpringBootParentToQuarkusBOM() {
        rewriteRun(
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
              </project>
              """,
            spec -> spec.after(actual -> {
                Matcher versionMatcher = Pattern.compile("<artifactId>quarkus-bom</artifactId>\\s*<version>(3\\.[^<]+)</version>").matcher(actual);
                assertThat(versionMatcher.find()).isTrue();
                String quarkusVersion = versionMatcher.group(1);
                return """
                  <project>
                      <groupId>com.example</groupId>
                      <artifactId>demo</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                      <properties>
                          <java.version>17</java.version>
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
                      <build>
                          <plugins>
                              <plugin>
                                  <groupId>io.quarkus.platform</groupId>
                                  <artifactId>quarkus-maven-plugin</artifactId>
                                  <version>%s</version>
                              </plugin>
                          </plugins>
                      </build>
                  </project>
                  """.formatted(quarkusVersion, quarkusVersion);
            })
          )
        );
    }

    @Test
    void migrateSpringBootWithExistingDependencyManagement() {
        rewriteRun(
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
              </project>
              """,
            spec -> spec.after(actual -> {
                Matcher versionMatcher = Pattern.compile("<artifactId>quarkus-bom</artifactId>\\s*<version>(3\\.[^<]+)</version>").matcher(actual);
                assertThat(versionMatcher.find()).isTrue();
                String quarkusVersion = versionMatcher.group(1);
                return """
                  <project>
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
                              <dependency>
                                  <groupId>io.quarkus.platform</groupId>
                                  <artifactId>quarkus-bom</artifactId>
                                  <version>%s</version>
                                  <type>pom</type>
                                  <scope>import</scope>
                              </dependency>
                          </dependencies>
                      </dependencyManagement>
                      <build>
                          <plugins>
                              <plugin>
                                  <groupId>io.quarkus.platform</groupId>
                                  <artifactId>quarkus-maven-plugin</artifactId>
                                  <version>%s</version>
                              </plugin>
                          </plugins>
                      </build>
                  </project>
                  """.formatted(quarkusVersion, quarkusVersion);
            })
          )
        );
    }

    @Test
    void migrateSpringBootDependency() {
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
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>3.1.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.quarkus.platform</groupId>
                                <artifactId>quarkus-bom</artifactId>
                                <version>3.28.3</version>
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
                        <dependency>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-spring-web</artifactId>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>io.quarkus.platform</groupId>
                                <artifactId>quarkus-maven-plugin</artifactId>
                                <version>3.28.3</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            ),
            srcMainJava(
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
    void doNotMigrateSpringBoot2x() {
        rewriteRun(
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
              </project>
              """
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
}
