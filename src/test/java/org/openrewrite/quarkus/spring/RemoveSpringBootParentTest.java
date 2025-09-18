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

class RemoveSpringBootParentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveSpringBootParent());
    }

    @DocumentExample
    @Test
    void removeSpringBootParent() {
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
                        <relativePath/>
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
                </project>
                """,
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
        );
    }

    @Test
    void doNotRemoveSpringBoot2xParent() {
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
                        <relativePath/>
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
                </project>
                """
            )
        );
    }

    @Test
    void doNotRemoveOtherSpringParent() {
        rewriteRun(
            //language=xml
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-framework-bom</artifactId>
                        <version>6.0.0</version>
                        <type>pom</type>
                        <scope>import</scope>
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
    void removeSpringBootParentWithComplexProject() {
        rewriteRun(
            //language=xml
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.2.1</version>
                        <relativePath/>
                    </parent>

                    <groupId>com.example</groupId>
                    <artifactId>complex-demo</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                    <packaging>jar</packaging>
                    <name>Complex Demo Application</name>
                    <description>Demo project with complex structure</description>

                    <properties>
                        <java.version>21</java.version>
                        <spring-cloud.version>2023.0.0</spring-cloud.version>
                        <testcontainers.version>1.19.3</testcontainers.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                    </dependencies>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.cloud</groupId>
                                <artifactId>spring-cloud-dependencies</artifactId>
                                <version>${spring-cloud.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

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
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>com.example</groupId>
                    <artifactId>complex-demo</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                    <packaging>jar</packaging>
                    <name>Complex Demo Application</name>
                    <description>Demo project with complex structure</description>

                    <properties>
                        <java.version>21</java.version>
                        <spring-cloud.version>2023.0.0</spring-cloud.version>
                        <testcontainers.version>1.19.3</testcontainers.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                    </dependencies>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.cloud</groupId>
                                <artifactId>spring-cloud-dependencies</artifactId>
                                <version>${spring-cloud.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

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
            )
        );
    }

    @Test
    void removeSpringBootParentWithMinimalNamespace() {
        rewriteRun(
            //language=xml
            pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.1.5</version>
                    </parent>

                    <groupId>com.example</groupId>
                    <artifactId>minimal-demo</artifactId>
                    <version>1.0.0</version>
                </project>
                """,
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>com.example</groupId>
                    <artifactId>minimal-demo</artifactId>
                    <version>1.0.0</version>
                </project>
                """
            )
        );
    }

}
