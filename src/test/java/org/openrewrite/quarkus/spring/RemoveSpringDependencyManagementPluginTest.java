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

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;

class RemoveSpringDependencyManagementPluginTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveSpringDependencyManagementPlugin());
    }

    @DocumentExample
    @Test
    void removePlugin() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'io.quarkus' version '4.0.1'
                  id 'io.spring.dependency-management' version '1.1.7'
              }
              """,
            """
              plugins {
                  id 'java'
                  id 'io.quarkus' version '4.0.1'
              }
              """
          )
        );
    }

    @Test
    void removePluginFromKotlinDsl() {
        rewriteRun(
          //language=kotlin
          buildGradleKts(
            """
              plugins {
                  java
                  id("io.quarkus") version "4.0.1"
                  id("io.spring.dependency-management") version "1.1.7"
              }
              """,
            """
              plugins {
                  java
                  id("io.quarkus") version "4.0.1"
              }
              """
          )
        );
    }

    @Test
    void keepPluginWithDependencyManagementBlock() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java'
                  id 'io.spring.dependency-management' version '1.1.7'
              }
              dependencyManagement {
                  imports {
                      mavenBom 'org.springframework.cloud:spring-cloud-dependencies:2025.0.0'
                  }
              }
              """
          )
        );
    }
}
