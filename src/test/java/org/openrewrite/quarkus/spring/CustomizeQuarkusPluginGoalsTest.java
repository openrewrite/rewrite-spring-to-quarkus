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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class CustomizeQuarkusPluginGoalsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath()
          .build()
          .activateRecipes("org.openrewrite.quarkus.spring.CustomizeQuarkusPluginGoals"));
    }

    @DocumentExample
    @Test
    void addNativeProfile() {
        rewriteRun(
          pomXml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <project xmlns="http://maven.apache.org/POM/4.0.0"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
              </project>
              """,
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <project xmlns="http://maven.apache.org/POM/4.0.0"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <profiles>
                      <profile>
                          <id>native</id>
                          <activation>
                              <property>
                                  <name>native</name>
                              </property>
                          </activation>
                          <properties>
                              <quarkus.package.type>native</quarkus.package.type>
                              <quarkus.native.enabled>true</quarkus.native.enabled>
                          </properties>
                      </profile>
                      <profile>
                          <id>container</id>
                          <activation>
                              <property>
                                  <name>container</name>
                              </property>
                          </activation>
                          <properties>
                              <quarkus.container-image.build>true</quarkus.container-image.build>
                          </properties>
                      </profile>
                  </profiles>
              </project>
              """
          )
        );
    }

    @Test
    void doNotDuplicateExistingProfile() {
        rewriteRun(
          pomXml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <project xmlns="http://maven.apache.org/POM/4.0.0"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <profiles>
                      <profile>
                          <id>native</id>
                          <activation>
                              <property>
                                  <name>native</name>
                              </property>
                          </activation>
                          <properties>
                              <quarkus.package.type>native</quarkus.package.type>
                              <quarkus.native.enabled>true</quarkus.native.enabled>
                          </properties>
                      </profile>
                      <profile>
                          <id>container</id>
                          <activation>
                              <property>
                                  <name>container</name>
                              </property>
                          </activation>
                          <properties>
                              <quarkus.container-image.build>true</quarkus.container-image.build>
                          </properties>
                      </profile>
                  </profiles>
              </project>
              """
          )
        );
    }
}
