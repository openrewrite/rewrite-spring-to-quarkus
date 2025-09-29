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

class SpringAnnotationsToCdiTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.SpringAnnotationsToCdi")
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-context", "spring-beans", "javax.persistence-api", "validation-api"));
    }

    @DocumentExample
    @Test
    void migrateServiceToApplicationScoped() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.stereotype.Service;

              @Service
              class UserService {
                  void doSomething() {
                      // business logic
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;

              @ApplicationScoped
              class UserService {
                  void doSomething() {
                      // business logic
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateComponentToApplicationScoped() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.stereotype.Component;

              @Component
              class UtilityComponent {
                  void utility() {
                      // utility logic
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;

              @ApplicationScoped
              class UtilityComponent {
                  void utility() {
                      // utility logic
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateRepositoryToApplicationScoped() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.stereotype.Repository;

              @Repository
              class UserRepository {
                  void save() {
                      // repository logic
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;

              @ApplicationScoped
              class UserRepository {
                  void save() {
                      // repository logic
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateAutowiredToInject() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.stereotype.Service;

              @Service
              class UserService {
                  @Autowired
                  private UserRepository userRepository;

                  void doSomething() {
                      userRepository.save();
                  }
              }

              class UserRepository {
                  void save() {}
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.inject.Inject;

              @ApplicationScoped
              class UserService {
                  @Inject
                  private UserRepository userRepository;

                  void doSomething() {
                      userRepository.save();
                  }
              }

              class UserRepository {
                  void save() {}
              }
              """
          )
        );
    }

    @Test
    void migrateValueToConfigProperty() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Value;
              import org.springframework.stereotype.Service;

              @Service
              class ConfigService {
                  @Value("${app.name}")
                  private String appName;

                  @Value("${app.timeout:30}")
                  private int timeout;

                  void printConfig() {
                      System.out.println(appName + " - " + timeout);
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import org.eclipse.microprofile.config.inject.ConfigProperty;

              @ApplicationScoped
              class ConfigService {
                  @ConfigProperty(name = "app.name")
                  private String appName;

                  @ConfigProperty(name = "app.timeout", defaultValue = "30")
                  private int timeout;

                  void printConfig() {
                      System.out.println(appName + " - " + timeout);
                  }
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
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Value;
              import org.springframework.stereotype.Service;

              @Service
              class OrderService {
                  private final UserRepository userRepository;
                  private final String serviceName;

                  @Autowired
                  OrderService(UserRepository userRepository, @Value("${service.name}") String serviceName) {
                      this.userRepository = userRepository;
                      this.serviceName = serviceName;
                  }

                  void processOrder() {
                      userRepository.save();
                  }
              }

              class UserRepository {
                  void save() {}
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.inject.Inject;
              import org.eclipse.microprofile.config.inject.ConfigProperty;

              @ApplicationScoped
              class OrderService {
                  private final UserRepository userRepository;
                  private final String serviceName;

                  @Inject
                  OrderService(UserRepository userRepository, @ConfigProperty(name = "service.name") String serviceName) {
                      this.userRepository = userRepository;
                      this.serviceName = serviceName;
                  }

                  void processOrder() {
                      userRepository.save();
                  }
              }

              class UserRepository {
                  void save() {}
              }
              """
          )
        );
    }

    @Test
    void migrateMultipleSpringAnnotationsInSameClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Value;
              import org.springframework.stereotype.Service;

              @Service
              class ComplexService {
                  @Autowired
                  private UserRepository userRepository;

                  @Value("${app.environment:dev}")
                  private String environment;

                  @Autowired
                  private ConfigService configService;

                  void execute() {
                      // business logic
                  }
              }

              interface UserRepository {
                  void save();
              }

              interface ConfigService {
                  void configure();
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.inject.Inject;
              import org.eclipse.microprofile.config.inject.ConfigProperty;

              @ApplicationScoped
              class ComplexService {
                  @Inject
                  private UserRepository userRepository;

                  @ConfigProperty(name = "app.environment", defaultValue = "dev")
                  private String environment;

                  @Inject
                  private ConfigService configService;

                  void execute() {
                      // business logic
                  }
              }

              interface UserRepository {
                  void save();
              }

              interface ConfigService {
                  void configure();
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonSpringAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.persistence.Entity;
              import javax.validation.constraints.NotNull;

              @Entity
              class User {
                  @NotNull
                  private String name;

                  String getName() {
                      return name;
                  }
              }
              """
          )
        );
    }
}
