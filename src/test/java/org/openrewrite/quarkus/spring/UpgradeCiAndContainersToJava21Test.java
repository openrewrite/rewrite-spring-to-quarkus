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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.docker.Assertions.docker;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.yaml.Assertions.yaml;

class UpgradeCiAndContainersToJava21Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.UpgradeCiAndContainersToJava21")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-boot-3",
            "spring-boot-autoconfigure-3"));
    }

    private static SourceSpecs springBootApplication() {
        return mavenProject("demo",
          //language=xml
          pomXml(
            """
              <project>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>3.5.4</version>
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
        );
    }

    @DocumentExample
    @Test
    void upgradeWorkflowAndDockerfile() {
        rewriteRun(
          springBootApplication(),
          docker(
            """
              FROM maven:3.9-eclipse-temurin-17 AS build
              COPY . .
              RUN mvn package

              FROM eclipse-temurin:17-jre-alpine
              COPY --from=build target/quarkus-app /app
              """,
            """
              FROM maven:3.9-eclipse-temurin-21 AS build
              COPY . .
              RUN mvn package

              FROM eclipse-temurin:21-jre-alpine
              COPY --from=build target/quarkus-app /app
              """
          ),
          //language=yaml
          yaml(
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-java@v4
                      with:
                        distribution: temurin
                        java-version: 17
              """,
            """
              jobs:
                build:
                  runs-on: ubuntu-latest
                  steps:
                    - uses: actions/checkout@v4
                    - uses: actions/setup-java@v4
                      with:
                        distribution: temurin
                        java-version: 21
              """,
            spec -> spec.path(".github/workflows/ci.yml")
          )
        );
    }

    @Test
    void upgradeJavaBaseImages() {
        rewriteRun(
          springBootApplication(),
          docker(
            """
              FROM gradle:8.14-jdk17-alpine AS build
              FROM maven:3-amazoncorretto-17-al2023 AS package
              FROM amazoncorretto:17
              FROM bellsoft/liberica-openjdk-alpine:17
              FROM openjdk:17-jdk-slim
              FROM eclipse-temurin:17.0.9_9-jre
              """,
            """
              FROM gradle:8.14-jdk21-alpine AS build
              FROM maven:3-amazoncorretto-21-al2023 AS package
              FROM amazoncorretto:21
              FROM bellsoft/liberica-openjdk-alpine:21
              FROM eclipse-temurin:21-jdk
              FROM eclipse-temurin:21-jre
              """
          )
        );
    }

    @Test
    void upgradeCiContainerImage() {
        rewriteRun(
          springBootApplication(),
          //language=yaml
          yaml(
            """
              build:
                image: maven:3.9-eclipse-temurin-17
                script:
                  - mvn package
              """,
            """
              build:
                image: maven:3.9-eclipse-temurin-21
                script:
                  - mvn package
              """,
            spec -> spec.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void doNotChangeNewerImages() {
            rewriteRun(
              springBootApplication(),
              docker(
                """
                  FROM eclipse-temurin:21-jre
                  FROM eclipse-temurin:25
                  """
              )
            );
        }

        @Test
        void doNotChangeRepositoryWithoutSpringBootApplication() {
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
                                <artifactId>spring-boot-starter</artifactId>
                                <version>3.5.4</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """
                )
              ),
              docker(
                """
                  FROM eclipse-temurin:17-jre
                  """
              ),
              //language=yaml
              yaml(
                """
                  jobs:
                    build:
                      runs-on: ubuntu-latest
                      steps:
                        - uses: actions/setup-java@v4
                          with:
                            java-version: 17
                  """,
                spec -> spec.path(".github/workflows/ci.yml")
              )
            );
        }
    }
}
