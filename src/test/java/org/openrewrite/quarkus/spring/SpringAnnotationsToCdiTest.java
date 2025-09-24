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
                package com.example.demo;

                import org.springframework.stereotype.Service;

                @Service
                public class UserService {
                    
                    public void doSomething() {
                        // business logic
                    }
                }
                """,
                """
                package com.example.demo;

                import jakarta.enterprise.context.ApplicationScoped;

                @ApplicationScoped
                public class UserService {
                    
                    public void doSomething() {
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
                package com.example.demo;

                import org.springframework.stereotype.Component;

                @Component
                public class UtilityComponent {
                    
                    public void utility() {
                        // utility logic
                    }
                }
                """,
                """
                package com.example.demo;

                import jakarta.enterprise.context.ApplicationScoped;

                @ApplicationScoped
                public class UtilityComponent {
                    
                    public void utility() {
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
                package com.example.demo;

                import org.springframework.stereotype.Repository;

                @Repository
                public class UserRepository {
                    
                    public void save() {
                        // repository logic
                    }
                }
                """,
                """
                package com.example.demo;

                import jakarta.enterprise.context.ApplicationScoped;

                @ApplicationScoped
                public class UserRepository {
                    
                    public void save() {
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
                package com.example.demo;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Service;

                @Service
                public class UserService {
                    
                    @Autowired
                    private UserRepository userRepository;
                    
                    public void doSomething() {
                        userRepository.save();
                    }
                }

                class UserRepository {
                    public void save() {}
                }
                """,
                """
                package com.example.demo;

                import jakarta.enterprise.context.ApplicationScoped;
                import jakarta.inject.Inject;

                @ApplicationScoped
                public class UserService {
                    
                    @Inject
                    private UserRepository userRepository;
                    
                    public void doSomething() {
                        userRepository.save();
                    }
                }

                class UserRepository {
                    public void save() {}
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
                package com.example.demo;

                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Service;

                @Service
                public class ConfigService {
                    
                    @Value("${app.name}")
                    private String appName;
                    
                    @Value("${app.timeout:30}")
                    private int timeout;
                    
                    public void printConfig() {
                        System.out.println(appName + " - " + timeout);
                    }
                }
                """,
                """
                package com.example.demo;

                import jakarta.enterprise.context.ApplicationScoped;
                import org.eclipse.microprofile.config.inject.ConfigProperty;

                @ApplicationScoped
                public class ConfigService {
                    
                    @ConfigProperty(name = "app.name")
                    private String appName;
                    
                    @ConfigProperty(name = "app.timeout", defaultValue = "30")
                    private int timeout;
                    
                    public void printConfig() {
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
                package com.example.demo;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {
                    
                    private final UserRepository userRepository;
                    private final String serviceName;
                    
                    @Autowired
                    public OrderService(UserRepository userRepository, @Value("${service.name}") String serviceName) {
                        this.userRepository = userRepository;
                        this.serviceName = serviceName;
                    }
                    
                    public void processOrder() {
                        userRepository.save();
                    }
                }

                class UserRepository {
                    public void save() {}
                }
                """,
                """
                package com.example.demo;

                import jakarta.enterprise.context.ApplicationScoped;
                import jakarta.inject.Inject;
                import org.eclipse.microprofile.config.inject.ConfigProperty;

                @ApplicationScoped
                public class OrderService {
                    
                    private final UserRepository userRepository;
                    private final String serviceName;
                    
                    @Inject
                    public OrderService(UserRepository userRepository, @ConfigProperty(name = "service.name") String serviceName) {
                        this.userRepository = userRepository;
                        this.serviceName = serviceName;
                    }
                    
                    public void processOrder() {
                        userRepository.save();
                    }
                }

                class UserRepository {
                    public void save() {}
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
                package com.example.demo;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Service;

                @Service
                public class ComplexService {
                    
                    @Autowired
                    private UserRepository userRepository;
                    
                    @Value("${app.environment:dev}")
                    private String environment;
                    
                    @Autowired
                    private ConfigService configService;
                    
                    public void execute() {
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
                package com.example.demo;

                import jakarta.enterprise.context.ApplicationScoped;
                import jakarta.inject.Inject;
                import org.eclipse.microprofile.config.inject.ConfigProperty;

                @ApplicationScoped
                public class ComplexService {
                    
                    @Inject
                    private UserRepository userRepository;
                    
                    @ConfigProperty(name = "app.environment", defaultValue = "dev")
                    private String environment;
                    
                    @Inject
                    private ConfigService configService;
                    
                    public void execute() {
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
                package com.example.demo;

                import javax.persistence.Entity;
                import javax.validation.constraints.NotNull;

                @Entity
                public class User {
                    
                    @NotNull
                    private String name;
                    
                    public String getName() {
                        return name;
                    }
                }
                """
            )
        );
    }
}