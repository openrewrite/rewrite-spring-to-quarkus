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
        spec.recipeFromResources("org.openrewrite.quarkus.spring.StereotypeAnnotationsToCDI")
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
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;

              @ApplicationScoped
              class UserService {
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
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;

              @ApplicationScoped
              class UtilityComponent {
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
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;

              @ApplicationScoped
              class UserRepository {
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
    void migrateConstructorInjection() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.stereotype.Service;

              @Service
              class OrderService {
                  private final UserRepository userRepository;

                  @Autowired
                  OrderService(UserRepository userRepository) {
                      this.userRepository = userRepository;
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
              class OrderService {
                  private final UserRepository userRepository;

                  @Inject
                  OrderService(UserRepository userRepository) {
                      this.userRepository = userRepository;
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
