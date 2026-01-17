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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateSpringTestingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath()
                        .build()
                        .activateRecipes("org.openrewrite.quarkus.spring.MigrateSpringTesting"))
                .parser(JavaParser.fromJavaVersion()
                        .classpath("spring-boot-test", "spring-boot-test-autoconfigure", "junit-jupiter-api"));
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
                                import io.quarkus.test.junit.QuarkusTest;
                                import io.quarkus.test.junit.mockito.InjectMock;
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
}
