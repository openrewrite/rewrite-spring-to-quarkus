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

import static org.openrewrite.java.Assertions.java;

class RemoveSpringBootApplicationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveSpringBootApplication())
            .parser(org.openrewrite.java.JavaParser.fromJavaVersion()
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
                package com.example.demo;

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
                package com.example.demo;

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
                package com.example.demo;

                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class DemoApplication {

                    public void someOtherMethod() {
                        System.out.println("Hello");
                    }
                }
                """,
                """
                package com.example.demo;

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
    void doNotChangeNonSpringBootClass() {
        rewriteRun(
            //language=java
            java(
                """
                package com.example.demo;

                public class RegularClass {

                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                """
            )
        );
    }
}
