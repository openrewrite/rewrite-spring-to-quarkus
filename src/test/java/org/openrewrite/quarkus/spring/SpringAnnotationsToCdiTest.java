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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringAnnotationsToCdiTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.quarkus.spring.StereotypeAnnotationsToCDI")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-context-6",
              "spring-beans-6",
              "spring-web-6",
              "spring-boot-autoconfigure-3"));
    }

    @DocumentExample
    @Test
    void migrateConfigurationWithBeans() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.client.RestTemplate;

              @Configuration
              public class AppConfig {
                  @Bean
                  public RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.web.client.RestTemplate;

              @ApplicationScoped
              public class AppConfig {
                  @Produces
                  @ApplicationScoped
                  public RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

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

    @Test
    void removeAutowiredFromInjectedParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.annotation.Bean;
              import org.springframework.stereotype.Component;
              import org.springframework.web.client.RestTemplate;

              @Component
              class Clients {
                  private final UserRepository userRepository;

                  Clients(@Autowired UserRepository userRepository) {
                      this.userRepository = userRepository;
                  }

                  @Bean
                  RestTemplate restTemplate(@Autowired UserRepository userRepository) {
                      return new RestTemplate();
                  }
              }

              class UserRepository {
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.web.client.RestTemplate;

              @ApplicationScoped
              class Clients {
                  private final UserRepository userRepository;

                  Clients(UserRepository userRepository) {
                      this.userRepository = userRepository;
                  }

                  @Produces
                  @ApplicationScoped
                  RestTemplate restTemplate(UserRepository userRepository) {
                      return new RestTemplate();
                  }
              }

              class UserRepository {
              }
              """
          )
        );
    }

    @Test
    void doNotChangeAutowiredTestMethodParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.web.client.RestTemplate;

              class ActuatorServiceApplicationTests {
                  void shouldReturn200WhenSendingRequestToController(@Autowired RestTemplate rest) {
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.web.client.RestTemplate;

              class ActuatorServiceApplicationTests {
                  void shouldReturn200WhenSendingRequestToController(/*~~(CDI does not inject method parameters; inject an `@Inject` field instead)~~>*/@Autowired RestTemplate rest) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOptionalAutowired() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.ConfigurableApplicationContext;
              import org.springframework.context.annotation.Bean;
              import org.springframework.web.client.RestTemplate;

              import java.util.List;

              class IntegrationTestsSupport {
                  @Autowired(required = false)
                  private ConfigurableApplicationContext applicationContext;

                  @Bean
                  RestTemplate restTemplate(@Autowired(required = false) List<RestTemplate> mcpSyncClients) {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.context.ConfigurableApplicationContext;
              import org.springframework.web.client.RestTemplate;

              import java.util.List;

              class IntegrationTestsSupport {
                  /*~~(Optional injection; migrate to `@Inject Instance<T>` manually)~~>*/@Autowired(required = false)
                  private ConfigurableApplicationContext applicationContext;

                  @Produces
                  @ApplicationScoped
                  RestTemplate restTemplate(/*~~(Optional injection; migrate to `@Inject Instance<T>` manually)~~>*/@Autowired(required = false) List<RestTemplate> mcpSyncClients) {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void configurationWithoutNoArgConstructorBecomesSingleton() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Configuration;

              @Configuration
              public class RouteConfig {
                  private final CoffeeService coffeeService;

                  public RouteConfig(CoffeeService coffeeService) {
                      this.coffeeService = coffeeService;
                  }
              }

              class CoffeeService {
              }
              """,
            """
              import jakarta.inject.Singleton;

              @Singleton
              public class RouteConfig {
                  private final CoffeeService coffeeService;

                  public RouteConfig(CoffeeService coffeeService) {
                      this.coffeeService = coffeeService;
                  }
              }

              class CoffeeService {
              }
              """
          )
        );
    }

    @Test
    void doNotChangeConditionalConfiguration() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.client.RestTemplate;

              @Configuration
              @ConditionalOnProperty("feature.enabled")
              class FeatureConfig {
                  @Bean
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.web.client.RestTemplate;

              /*~~(CDI has no equivalent of Spring conditional configuration; migrate it manually, e.g. to `@IfBuildProperty`)~~>*/@Configuration
              @ConditionalOnProperty("feature.enabled")
              class FeatureConfig {
                  @Bean
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeSpringSecurityConfiguration() {
        rewriteRun(
          java(SpringBeanToCdiProducesTest.SECURITY_FILTER_CHAIN_STUB),
          java(SpringBeanToCdiProducesTest.HTTP_SECURITY_STUB),
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              @Configuration
              class WebSecurityConfig {
                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;

              /*~~(Spring Security configuration has no CDI equivalent; migrate it to Quarkus security manually)~~>*/@Configuration
              class WebSecurityConfig {
                  /*~~(Spring Security configuration has no CDI equivalent; migrate it to Quarkus security manually)~~>*/@Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      return http.build();
                  }
              }
              """
          )
        );
    }
}
