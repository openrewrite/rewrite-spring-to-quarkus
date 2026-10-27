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

class SpringBeanToCdiProducesTest implements RewriteTest {

    //language=java
    static final String SECURITY_FILTER_CHAIN_STUB = """
      package org.springframework.security.web;
      public interface SecurityFilterChain {}
      """;

    //language=java
    static final String HTTP_SECURITY_STUB = """
      package org.springframework.security.config.annotation.web.builders;
      import org.springframework.security.web.SecurityFilterChain;
      public class HttpSecurity {
          public SecurityFilterChain build() { return null; }
      }
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringBeanToCdiProduces())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-context-6", "spring-web-6", "spring-boot-autoconfigure-3"));
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

    @Test
    void keepOtherAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.DependsOn;
              import org.springframework.context.annotation.Lazy;
              import org.springframework.context.annotation.Primary;
              import org.springframework.context.annotation.Profile;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  @Lazy
                  @Primary
                  @DependsOn("keyspaceCreator")
                  RestTemplate cassandraSession() {
                      return new RestTemplate();
                  }

                  @Profile("mysql")
                  @Bean
                  static RestTemplate container() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.ApplicationScoped;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.context.annotation.DependsOn;
              import org.springframework.context.annotation.Lazy;
              import org.springframework.context.annotation.Primary;
              import org.springframework.context.annotation.Profile;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces
                  @ApplicationScoped
                  @Lazy
                  @Primary
                  @DependsOn("keyspaceCreator")
                  RestTemplate cassandraSession() {
                      return new RestTemplate();
                  }

                  @Profile("mysql")
                  @Produces
                  @ApplicationScoped
                  static RestTemplate container() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepOtherAnnotationsOnOneLine() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Primary;
              import org.springframework.context.annotation.Scope;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Scope("prototype") @Bean @Primary
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.Dependent;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.context.annotation.Primary;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces @Dependent @Primary
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepSingleBeanAnnotationOnDeclarationLine() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean RestTemplate restTemplate() {
                      return new RestTemplate();
                  }

                  @Bean("named") public RestTemplate namedTemplate() {
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
                  @Produces @ApplicationScoped RestTemplate restTemplate() {
                      return new RestTemplate();
                  }

                  @Produces @Named("named") @ApplicationScoped public RestTemplate namedTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void reuseNonLiteralBeanName() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  static final String BUS_PATH_MATCHER_NAME = "busPathMatcher";

                  @Bean(name = BUS_PATH_MATCHER_NAME)
                  RestTemplate busPathMatcher() {
                      return new RestTemplate();
                  }

                  @Bean({"primaryTemplate", "alias"})
                  RestTemplate primaryTemplate() {
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
                  static final String BUS_PATH_MATCHER_NAME = "busPathMatcher";

                  @Produces
                  @Named(BUS_PATH_MATCHER_NAME)
                  @ApplicationScoped
                  RestTemplate busPathMatcher() {
                      return new RestTemplate();
                  }

                  @Produces
                  @Named("primaryTemplate")
                  @ApplicationScoped
                  RestTemplate primaryTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateNamedScopeAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Scope;
              import org.springframework.context.annotation.ScopedProxyMode;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
                  RestTemplate requestTemplate() {
                      return new RestTemplate();
                  }

                  @Bean
                  @Scope(scopeName = "prototype")
                  RestTemplate prototypeTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import jakarta.enterprise.context.Dependent;
              import jakarta.enterprise.context.RequestScoped;
              import jakarta.enterprise.inject.Produces;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Produces
                  @RequestScoped
                  RestTemplate requestTemplate() {
                      return new RestTemplate();
                  }

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

    @Test
    void markDestroyMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean(destroyMethod = "close")
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
                  /*~~(Spring called `destroyMethod = "close"` on this bean; call it from the producer or a `@Disposes` method)~~>*/@Produces
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
    void doNotChangeUnknownScope() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Scope;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  @Scope("refresh")
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Scope;
              import org.springframework.web.client.RestTemplate;

              class AppConfig {
                  @Bean
                  /*~~(CDI has no equivalent of the Spring `refresh` scope)~~>*/@Scope("refresh")
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeBeansOfConditionalOrSecurityConfiguration() {
        rewriteRun(
          java(SECURITY_FILTER_CHAIN_STUB),
          java(HTTP_SECURITY_STUB),
          //language=java
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
              import org.springframework.context.annotation.Bean;
              import org.springframework.web.client.RestTemplate;

              @ConditionalOnProperty("feature.enabled")
              class FeatureConfig {
                  @Bean
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }
              }
              """
          ),
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              import org.springframework.web.client.RestTemplate;

              class WebSecurityConfig {
                  @Bean
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }

                  @Bean
                  SecurityFilterChain securityFilterChain(HttpSecurity http) {
                      return http.build();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              import org.springframework.web.client.RestTemplate;

              class WebSecurityConfig {
                  @Bean
                  RestTemplate restTemplate() {
                      return new RestTemplate();
                  }

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
