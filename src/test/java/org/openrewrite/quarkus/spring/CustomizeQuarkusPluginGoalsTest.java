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

class CustomizeQuarkusPluginGoalsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.CustomizeQuarkusPluginGoals");
    }

    @DocumentExample
    @Test
    void addNativeProfile() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
              </project>
              """,
            """
              <project>
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
              <project>
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
