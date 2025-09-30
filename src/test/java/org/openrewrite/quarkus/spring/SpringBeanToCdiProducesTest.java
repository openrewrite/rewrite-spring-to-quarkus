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

class SpringBeanToCdiProducesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringBeanToCdiProduces())
          .parser(JavaParser.fromJavaVersion().classpath("spring-context", "spring-web"));
    }

    @DocumentExample
    @Test
    void migrateBeanToProduces() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces
                  @ApplicationScoped
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateBeanWithSingletonScope() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Scope;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  @Scope("singleton")
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces
                  @ApplicationScoped
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateBeanWithPrototypeScope() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Scope;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  @Scope("prototype")
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.Dependent;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces
                  @Dependent
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateMultipleBeanMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Scope;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }

                  @Bean
                  @Scope("prototype")
                  RestTemplate restTemplate2() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.enterprise.context.Dependent;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces
                  @ApplicationScoped
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }

                  @Produces
                  @Dependent
                  RestTemplate restTemplate2() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateBeanWithNamedQualifier() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean("customRestTemplate")
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.enterprise.inject.Produces;
              import jakarta.inject.Named;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces
                  @Named("customRestTemplate")
                  @ApplicationScoped
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateBeanWithScopeConstants() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.beans.factory.config.ConfigurableBeanFactory;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Scope;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
                  RestTemplate prototypeTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.Dependent;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces
                  @Dependent
                  RestTemplate prototypeTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }
}
