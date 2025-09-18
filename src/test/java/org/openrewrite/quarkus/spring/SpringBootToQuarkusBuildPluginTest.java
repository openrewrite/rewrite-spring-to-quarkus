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

import static org.openrewrite.maven.Assertions.pomXml;

class SpringBootToQuarkusBuildPluginTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.SpringBootToQuarkusBuildPlugin");
    }

    @DocumentExample
    @Test
    void replaceSpringBootMavenPlugin() {
        rewriteRun(
            //language=xml
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    
                    <properties>
                        <quarkus.platform.version>3.8.0</quarkus.platform.version>
                    </properties>
                    
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <version>3.1.0</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    
                    <properties>
                        <quarkus.platform.version>3.8.0</quarkus.platform.version>
                    </properties>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>io.quarkus.platform</groupId>
                                <artifactId>quarkus-maven-plugin</artifactId>
                                <version>${quarkus.platform.version}</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            )
        );
    }

    @Test
    void doNotChangeProjectWithoutSpringBootPlugin() {
        rewriteRun(
            //language=xml
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            )
        );
    }

    @Test
    void doNotChangeProjectWithoutBuildSection() {
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

    @Test
    void replaceSpringBootMavenPluginWithExistingPlugins() {
        rewriteRun(
            //language=xml
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    
                    <properties>
                        <quarkus.platform.version>3.8.0</quarkus.platform.version>
                    </properties>
                    
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                            </plugin>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <version>3.1.0</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    
                    <properties>
                        <quarkus.platform.version>3.8.0</quarkus.platform.version>
                    </properties>
                    
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                            </plugin>
                            <plugin>
                                <groupId>io.quarkus.platform</groupId>
                                <artifactId>quarkus-maven-plugin</artifactId>
                                <version>${quarkus.platform.version}</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            )
        );
    }

    @Test
    void replaceSpringBootMavenPluginWithConfiguration() {
        rewriteRun(
            //language=xml
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    
                    <properties>
                        <quarkus.platform.version>3.8.0</quarkus.platform.version>
                    </properties>
                    
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <version>3.1.0</version>
                                <configuration>
                                    <mainClass>com.example.Application</mainClass>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """,
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    
                    <properties>
                        <quarkus.platform.version>3.8.0</quarkus.platform.version>
                    </properties>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>io.quarkus.platform</groupId>
                                <artifactId>quarkus-maven-plugin</artifactId>
                                <version>${quarkus.platform.version}</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
            )
        );
    }

    // TODO: Add Gradle tests when Gradle support is implemented
}