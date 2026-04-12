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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-spring-to-quarkus/issues/66")
class ConfigurationPropertiesToConfigMappingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConfigurationPropertiesToConfigMapping())
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot"));
    }

    @DocumentExample
    @Test
    void convertClassToInterface() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties(prefix = "app")
              public class AppProperties {
                  private String name;
                  private int timeout;

                  public String getName() {
                      return name;
                  }

                  public void setName(String name) {
                      this.name = name;
                  }

                  public int getTimeout() {
                      return timeout;
                  }

                  public void setTimeout(int timeout) {
                      this.timeout = timeout;
                  }
              }
              """,
            """
              import io.smallrye.config.ConfigMapping;

              @ConfigMapping(prefix = "app")
              public interface AppProperties {
                  String name();

                  int timeout();
              }
              """
          )
        );
    }

    @Test
    void convertConstructorBasedClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties(prefix = "server")
              public class ServerConfig {
                  private final String host;
                  private final int port;

                  public ServerConfig(String host, int port) {
                      this.host = host;
                      this.port = port;
                  }

                  public String getHost() {
                      return host;
                  }

                  public int getPort() {
                      return port;
                  }
              }
              """,
            """
              import io.smallrye.config.ConfigMapping;

              @ConfigMapping(prefix = "server")
              public interface ServerConfig {
                  String host();

                  int port();
              }
              """
          )
        );
    }

    @Test
    void convertBooleanIsGetter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties(prefix = "feature")
              public class FeatureFlags {
                  private boolean enabled;

                  public boolean isEnabled() {
                      return enabled;
                  }

                  public void setEnabled(boolean enabled) {
                      this.enabled = enabled;
                  }
              }
              """,
            """
              import io.smallrye.config.ConfigMapping;

              @ConfigMapping(prefix = "feature")
              public interface FeatureFlags {
                  boolean enabled();
              }
              """
          )
        );
    }

    @Test
    void removeIgnoreUnknownFieldsAttribute() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.boot.context.properties.ConfigurationProperties;

              @ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
              public class ApplicationProperties {
              }
              """,
            """
              import io.smallrye.config.ConfigMapping;

              @ConfigMapping(prefix = "application")
              public interface ApplicationProperties {
              }
              """
          )
        );
    }

    @Test
    void unchangedWithoutAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              public class RegularConfig {
                  private String name;
                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """
          )
        );
    }
}
