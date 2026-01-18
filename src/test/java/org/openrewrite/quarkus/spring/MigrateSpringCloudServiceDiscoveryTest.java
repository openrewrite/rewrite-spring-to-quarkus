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

class MigrateSpringCloudServiceDiscoveryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath()
                        .build()
                        .activateRecipes("org.openrewrite.quarkus.spring.MigrateSpringCloudServiceDiscovery"))
                .parser(JavaParser.fromJavaVersion()
                        .classpath("spring-cloud-commons", "spring-cloud-netflix-eureka-client"));
    }

    @DocumentExample
    @Test
    void removeEnableDiscoveryClientAnnotation() {
        rewriteRun(
                //language=java
                java(
                        """
                                import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

                                @EnableDiscoveryClient
                                public class Application {
                                    public static void main(String[] args) {
                                        // Application startup
                                    }
                                }
                                """,
                        """
                                public class Application {
                                    public static void main(String[] args) {
                                        // Application startup
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void doNotChangeNonDiscoveryClass() {
        rewriteRun(
                //language=java
                java(
                        """
                                public class RegularClass {
                                    public void doSomething() {
                                        System.out.println("Hello");
                                    }
                                }
                                """
                )
        );
    }
}
