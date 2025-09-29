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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringValueToCdiConfigPropertyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringValueToCdiConfigProperty())
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans"));
    }

    @DocumentExample
    @Test
    void migrateValueToConfigProperty() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Value;

              class ConfigService {
                  @Value("${app.name}")
                  private String appName;

                  @Value("${app.timeout:30}")
                  private int timeout;
              }
              """,
            """
              import org.eclipse.microprofile.config.inject.ConfigProperty;

              class ConfigService {
                  @ConfigProperty(name = "app.name")
                  private String appName;

                  @ConfigProperty(name = "app.timeout", defaultValue = "30")
                  private int timeout;
              }
              """
          )
        );
    }

    @Test
    void migrateConstructorInjection() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Value;

              class OrderService {
                  private final UserRepository userRepository;
                  private final String serviceName;

                  OrderService(UserRepository userRepository, @Value("${service.name}") String serviceName) {
                      this.userRepository = userRepository;
                      this.serviceName = serviceName;
                  }
              }

              class UserRepository {
                  void save() {}
              }
              """,
            """
              import org.eclipse.microprofile.config.inject.ConfigProperty;

              class OrderService {
                  private final UserRepository userRepository;
                  private final String serviceName;

                  OrderService(UserRepository userRepository, @ConfigProperty(name = "service.name") String serviceName) {
                      this.userRepository = userRepository;
                      this.serviceName = serviceName;
                  }
              }

              class UserRepository {
                  void save() {}
              }
              """
          )
        );
    }
}
