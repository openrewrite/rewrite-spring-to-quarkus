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
import static org.openrewrite.maven.Assertions.pomXml;

class SpringBootToQuarkusTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.SpringBootToQuarkusBOM");
    }

    @DocumentExample
    @Test
    void migrateSpringBootParentToQuarkusBOM() {
        rewriteRun(
            //language=xml
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>

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
                </project>
                """,
                spec -> spec.after(actual -> {
                    Matcher versionMatcher = Pattern.compile("<artifactId>quarkus-bom</artifactId>\\s*<version>(3\\.[^<]+)</version>").matcher(actual);
                    assertThat(versionMatcher.find()).isTrue();
                    String quarkusVersion = versionMatcher.group(1);
                    return """
                <project>
                    <modelVersion>4.0.0</modelVersion>

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
                </project>
                """.formatted(quarkusVersion);
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
                    <modelVersion>4.0.0</modelVersion>

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
                </project>
                """,
                spec -> spec.after(actual -> {
                    Matcher versionMatcher = Pattern.compile("<artifactId>quarkus-bom</artifactId>\\s*<version>(3\\.[^<]+)</version>").matcher(actual);
                    assertThat(versionMatcher.find()).isTrue();
                    String quarkusVersion = versionMatcher.group(1);
                    return """
                <project>
                    <modelVersion>4.0.0</modelVersion>

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
                </project>
                """.formatted(quarkusVersion);
                })
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
                    <modelVersion>4.0.0</modelVersion>

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
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                </project>
                """
            )
        );
    }
}
