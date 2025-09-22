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
import static org.openrewrite.maven.Assertions.pomXml;

class EnableSchedulingToQuarkusTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertAnnotationToDependency())
            .parser(org.openrewrite.java.JavaParser.fromJavaVersion()
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
            //language=java
            java(
                """
                package com.example.demo;
                
                import org.springframework.scheduling.annotation.EnableScheduling;
                
                @EnableScheduling
                public class SchedulingConfig {
                }
                """,
                """
                package com.example.demo;
                
                public class SchedulingConfig {
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
        );
    }

    @Test
    void doNotChangeNonSchedulingClass() {
        rewriteRun(
            //language=java
            java(
                """
                package com.example.demo;
                
                public class RegularClass {
                    
                    public void someMethod() {
                        System.out.println("Hello");
                    }
                }
                """
            )
        );
    }
}