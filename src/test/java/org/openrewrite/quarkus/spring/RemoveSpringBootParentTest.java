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
import static org.openrewrite.properties.Assertions.properties;

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
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>

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
    void removeSpringBoot4Parent() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>

                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>4.0.0</version>
                      <relativePath/>
                  </parent>

                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>

                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
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
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <maven.compiler.release>21</maven.compiler.release>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
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
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void renameJavaVersionReferences() {
        rewriteRun(
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
                  <version>1.0.0</version>
                  <properties>
                      <java.version>21</java.version>
                      <enforcer.java.version>${java.version}</enforcer.java.version>
                  </properties>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <maven.compiler.release>21</maven.compiler.release>
                      <enforcer.java.version>${maven.compiler.release}</enforcer.java.version>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void keepJavaVersionWhenReleaseAlreadyDeclared() {
        rewriteRun(
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
                  <version>1.0.0</version>
                  <properties>
                      <java.version>21</java.version>
                      <maven.compiler.release>${java.version}</maven.compiler.release>
                      <maven.compiler.testRelease>${java.version}</maven.compiler.testRelease>
                  </properties>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <java.version>21</java.version>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <maven.compiler.release>${java.version}</maven.compiler.release>
                      <maven.compiler.testRelease>${java.version}</maven.compiler.testRelease>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
              </project>
              """
          )
        );
    }

    @Test
    void keepDeclaredEncodingAndCompilerParameters() {
        rewriteRun(
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
                  <version>1.0.0</version>
                  <properties>
                      <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                  </properties>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <parameters>false</parameters>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <project.build.sourceEncoding>ISO-8859-1</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <parameters>false</parameters>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          )
        );
    }

    @Test
    void renameJavaVersionInProfile() {
        rewriteRun(
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
                  <version>1.0.0</version>
                  <properties>
                      <java.version>21</java.version>
                  </properties>
                  <profiles>
                      <profile>
                          <id>java25</id>
                          <properties>
                              <java.version>25</java.version>
                          </properties>
                      </profile>
                  </profiles>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <maven.compiler.release>21</maven.compiler.release>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
                  <profiles>
                      <profile>
                          <id>java25</id>
                          <properties>
                              <maven.compiler.release>25</maven.compiler.release>
                          </properties>
                      </profile>
                  </profiles>
              </project>
              """
          )
        );
    }

    @Test
    void renameJavaVersionReferencesInChildModules() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>3.5.4</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>demo-parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>app</module>
                  </modules>
                  <properties>
                      <java.version>21</java.version>
                  </properties>
              </project>
              """,
            //language=xml
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo-parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>app</module>
                  </modules>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <maven.compiler.release>21</maven.compiler.release>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
              </project>
              """
          ),
          pomXml(
            //language=xml
            """
              <project>
                  <parent>
                      <groupId>com.example</groupId>
                      <artifactId>demo-parent</artifactId>
                      <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <properties>
                      <enforcer.java.version>${java.version}</enforcer.java.version>
                  </properties>
              </project>
              """,
            //language=xml
            """
              <project>
                  <parent>
                      <groupId>com.example</groupId>
                      <artifactId>demo-parent</artifactId>
                      <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <properties>
                      <enforcer.java.version>${maven.compiler.release}</enforcer.java.version>
                  </properties>
              </project>
              """,
            spec -> spec.path("app/pom.xml")
          )
        );
    }

    @Test
    void keepResourceFilteringForPlaceholders() {
        rewriteRun(
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
                  <version>1.0.0</version>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
                  <build>
                      <resources>
                          <resource>
                              <directory>src/main/resources</directory>
                              <filtering>true</filtering>
                              <includes>
                                  <include>**/application*.yml</include>
                                  <include>**/application*.yaml</include>
                                  <include>**/application*.properties</include>
                              </includes>
                          </resource>
                          <resource>
                              <directory>src/main/resources</directory>
                              <excludes>
                                  <exclude>**/application*.yml</exclude>
                                  <exclude>**/application*.yaml</exclude>
                                  <exclude>**/application*.properties</exclude>
                              </excludes>
                          </resource>
                      </resources>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-resources-plugin</artifactId>
                              <configuration>
                                  <delimiters>
                                      <delimiter>@</delimiter>
                                  </delimiters>
                                  <useDefaultDelimiters>false</useDefaultDelimiters>
                              </configuration>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          ),
          properties(
            "app.version=@project.version@",
            spec -> spec.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    void doNotAddResourceFilteringWhenResourcesPluginIsDeclared() {
        rewriteRun(
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
                  <version>1.0.0</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-resources-plugin</artifactId>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-resources-plugin</artifactId>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """
          ),
          properties(
            "app.version=@project.version@",
            spec -> spec.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    void addResourceFilteringOnlyForApplicationWithPlaceholders() {
        rewriteRun(
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
                  <artifactId>other</artifactId>
                  <version>1.0.0</version>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>other</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <maven.compiler.parameters>true</maven.compiler.parameters>
                      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                      <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
                  </properties>
              </project>
              """,
            spec -> spec.path("other/pom.xml")
          ),
          properties(
            "app.version=@project.version@",
            spec -> spec.path("demo/src/main/resources/application.properties")
          )
        );
    }
}
