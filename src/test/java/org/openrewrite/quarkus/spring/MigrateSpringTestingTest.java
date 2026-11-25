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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;

class MigrateSpringTestingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.MigrateSpringTesting")
          .parser(JavaParser.fromJavaVersion()
            .classpath(ListUtils.concatAll(
              JavaParser.dependenciesFromResources(new InMemoryExecutionContext(), "spring-boot-test-3", "spring-boot-test-autoconfigure-3"),
              JavaParser.dependenciesFromClasspath("junit-jupiter-api"))));
    }

    @DocumentExample
    @Test
    void convertSpringBootTestToQuarkusTest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;
              import org.junit.jupiter.api.Test;

              @SpringBootTest
              class MyApplicationTest {
                  @Test
                  void contextLoads() {
                  }
              }
              """,
            """
              import io.quarkus.test.junit.QuarkusTest;
              import org.junit.jupiter.api.Test;

              @QuarkusTest
              class MyApplicationTest {
                  @Test
                  void contextLoads() {
                  }
              }
              """
          )
        );
    }

    @Test
    void convertMockBeanToInjectMock() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;
              import org.springframework.boot.test.mock.mockito.MockBean;
              import org.junit.jupiter.api.Test;

              @SpringBootTest
              class ServiceTest {
                  @MockBean
                  private MyService myService;

                  @Test
                  void testService() {
                  }
              }
              """,
            """
              import io.quarkus.test.InjectMock;
              import io.quarkus.test.junit.QuarkusTest;
              import org.junit.jupiter.api.Test;

              @QuarkusTest
              class ServiceTest {
                  @InjectMock
                  private MyService myService;

                  @Test
                  void testService() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              public class MyService {
                  public String process() {
                      return "result";
                  }
              }
              """
          )
        );
    }

    @Test
    void gradleTestDependenciesUseQuarkus4() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          mavenProject("app",
            srcTestJava(
              //language=java
              java(
                """
                  import org.springframework.boot.test.mock.mockito.MockBean;

                  class ServiceTest {
                      @MockBean
                      Runnable runnable;
                  }
                  """,
                """
                  import io.quarkus.test.InjectMock;

                  class ServiceTest {
                      @InjectMock
                      Runnable runnable;
                  }
                  """
              )
            ),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    testImplementation 'org.springframework.boot:spring-boot-starter-test:3.5.4'
                }
                """,
              spec -> spec.after(gradle -> assertThat(gradle)
                .containsPattern("testImplementation [\"']io\\.quarkus:quarkus-junit:4(\\.\\d+)+[\"']")
                .containsPattern("testImplementation [\"']io\\.quarkus:quarkus-junit-mockito:4(\\.\\d+)+[\"']")
                .actual())
            )
          )
        );
    }
}
