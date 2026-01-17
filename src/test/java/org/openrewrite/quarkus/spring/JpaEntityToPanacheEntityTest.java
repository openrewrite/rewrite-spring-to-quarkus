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

class JpaEntityToPanacheEntityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JpaEntityToPanacheEntity())
                .parser(JavaParser.fromJavaVersion()
                        .classpath("jakarta.persistence-api", "quarkus-hibernate-orm-panache"));
    }

    @DocumentExample
    @Test
    void convertEntityToPanacheEntity() {
        rewriteRun(
                //language=java
                java(
                        """
                                import jakarta.persistence.Entity;
                                import jakarta.persistence.Id;
                                import jakarta.persistence.GeneratedValue;

                                @Entity
                                public class User {
                                    @Id
                                    @GeneratedValue
                                    private Long id;

                                    private String name;
                                    private String email;

                                    public Long getId() { return id; }
                                    public void setId(Long id) { this.id = id; }
                                    public String getName() { return name; }
                                    public void setName(String name) { this.name = name; }
                                    public String getEmail() { return email; }
                                    public void setEmail(String email) { this.email = email; }
                                }
                                """,
                        """
                                import io.quarkus.hibernate.orm.panache.PanacheEntity;
                                import jakarta.persistence.Entity;

                                @Entity
                                public class User extends PanacheEntity {

                                    private String name;
                                    private String email;
                                    public String getName() { return name; }
                                    public void setName(String name) { this.name = name; }
                                    public String getEmail() { return email; }
                                    public void setEmail(String email) { this.email = email; }
                                }
                                """
                )
        );
    }

    @Test
    void doNotConvertEntityWithExistingSuperclass() {
        rewriteRun(
                //language=java
                java(
                        """
                                import jakarta.persistence.Entity;
                                import jakarta.persistence.Id;

                                @Entity
                                public class SpecialUser extends BaseEntity {
                                    @Id
                                    private Long id;
                                    private String name;
                                }
                                """
                ),
                //language=java
                java(
                        """
                                public class BaseEntity {
                                    protected Long createdAt;
                                }
                                """
                )
        );
    }

    @Test
    void doNotChangeNonEntityClass() {
        rewriteRun(
                //language=java
                java(
                        """
                                public class RegularClass {
                                    private Long id;
                                    private String name;
                                }
                                """
                )
        );
    }
}
