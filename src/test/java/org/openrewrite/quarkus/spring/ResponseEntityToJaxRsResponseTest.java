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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ResponseEntityToJaxRsResponseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ResponseEntityToJaxRsResponse())
          .parser(JavaParser.fromJavaVersion().classpath("spring-web", "spring-context"));
    }

    @DocumentExample
    @Test
    void ok() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  @GetMapping("/products/{id}")
                  ResponseEntity<Product> getProduct() {
                      Product product = new Product();
                      return ResponseEntity.ok(product);
                  }
              }

              class Product {
                  String name;
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  @GetMapping("/products/{id}")
                  Response<Product> getProduct() {
                      Product product = new Product();
                      return Response.ok(product);
                  }
              }

              class Product {
                  String name;
              }
              """
          )
        );
    }

    @Test
    void notFound() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  @GetMapping("/products/{id}")
                  ResponseEntity<Product> getProduct() {
                      return ResponseEntity.notFound().build();
                  }
              }

              class Product {
                  String name;
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  @GetMapping("/products/{id}")
                  Response<Product> getProduct() {
                      return Response.status(Response.Status.NOT_FOUND).build();
                  }
              }

              class Product {
                  String name;
              }
              """
          )
        );
    }

    @Test
    void status() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  @PostMapping("/products")
                  ResponseEntity<Product> createProduct() {
                      Product product = new Product();
                      return ResponseEntity.status(HttpStatus.CREATED).body(product);
                  }
              }

              class Product {
                  String name;
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  @PostMapping("/products")
                  Response<Product> createProduct() {
                      Product product = new Product();
                      return Response.status(Response.Status.CREATED).entity(product);
                  }
              }

              class Product {
                  String name;
              }
              """
          )
        );
    }

    @Test
    void emptyOk() {
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  @GetMapping("/products")
                  ResponseEntity<Void> getProducts() {
                      return ResponseEntity.ok().build();
                  }
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  @GetMapping("/products")
                  Response<Void> getProducts() {
                      return Response.ok().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void badRequestAndNoContent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  ResponseEntity<String> badRequest() {
                      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("error");
                  }

                  ResponseEntity<Void> noContent() {
                      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
                  }
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class ProductController {
                  Response<String> badRequest() {
                      return Response.status(Response.Status.BAD_REQUEST).entity("error");
                  }

                  Response<Void> noContent() {
                      return Response.status(Response.Status.NO_CONTENT).build();
                  }
              }
              """
          )
        );
    }
}
