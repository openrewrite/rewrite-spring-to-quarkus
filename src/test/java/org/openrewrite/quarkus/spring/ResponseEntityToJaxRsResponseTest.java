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
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"unused", "NullableProblems"})
class ResponseEntityToJaxRsResponseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ResponseEntityToJaxRsResponse())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6", "spring-context-6", "spring-core-6"));
    }

    @DocumentExample
    @Test
    void builderChainGetsBuild() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RestController;

              import java.net.URI;

              @RestController
              class OrderController {
              	@PostMapping("/orders")
              	ResponseEntity<?> newOrder(URI location, String order) {
              		return ResponseEntity //
              				.created(location) //
              				.body(order);
              	}

              	@PostMapping("/orders/{id}/cancel")
              	ResponseEntity<?> cancel(@PathVariable Long id, String status) {
              		return ResponseEntity //
              				.status(HttpStatus.METHOD_NOT_ALLOWED) //
              				.header("Content-Type", "application/problem+json") //
              				.body("You can't cancel an order that is in the " //
              						+ status + " status");
              	}
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RestController;

              import java.net.URI;

              @RestController
              class OrderController {
              	@PostMapping("/orders")
              	Response newOrder(URI location, String order) {
              		return Response //
              				.created(location) //
              				.entity(order).build();
              	}

              	@PostMapping("/orders/{id}/cancel")
              	Response cancel(@PathVariable Long id, String status) {
              		return Response //
              				.status(Response.Status.METHOD_NOT_ALLOWED) //
              				.header("Content-Type", "application/problem+json") //
              				.entity("You can't cancel an order that is in the " //
              						+ status + " status").build();
              	}
              }
              """
          )
        );
    }

    @Test
    void okAndNotFoundThroughOptionalWithSelfLink() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              import java.util.List;
              import java.util.Optional;

              @RestController
              class EmployeeController {
                  @GetMapping("/employees")
                  ResponseEntity<List<String>> findAll(List<String> employees) {
                      return ResponseEntity.ok(employees);
                  }

                  @GetMapping("/employees/{id}")
                  ResponseEntity<String> findOne(Optional<String> employee, List<String> employees) {
                      String allEmployees = linkTo(findAll(employees));
                      return employee //
                              .map(e -> e + " of " + allEmployees) //
                              .map(ResponseEntity::ok) //
                              .orElse(ResponseEntity.notFound().build());
                  }

                  private static String linkTo(Object invocation) {
                      return invocation.toString();
                  }
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              import java.util.List;
              import java.util.Optional;

              @RestController
              class EmployeeController {
                  @GetMapping("/employees")
                  Response findAll(List<String> employees) {
                      return Response.ok(employees).build();
                  }

                  @GetMapping("/employees/{id}")
                  Response findOne(Optional<String> employee, List<String> employees) {
                      String allEmployees = linkTo(findAll(employees));
                      return employee //
                              .map(e -> e + " of " + allEmployees) //
                              .map(body -> Response.ok(body).build()) //
                              .orElse(Response.status(Response.Status.NOT_FOUND).build());
                  }

                  private static String linkTo(Object invocation) {
                      return invocation.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void constructors() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpEntity;
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.DeleteMapping;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.PutMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class GreetingController {
                  @GetMapping("/greeting")
                  public HttpEntity<String> greeting(String name) {
                      String greeting = "Hello, " + name;
                      return new ResponseEntity<>(greeting, HttpStatus.OK);
                  }

                  @PutMapping("/greeting")
                  public ResponseEntity<String> replace(String greeting) {
                      return new ResponseEntity<String>(greeting, HttpStatus.CONFLICT);
                  }

                  @DeleteMapping("/greeting")
                  public ResponseEntity<Void> delete() {
                      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
                  }
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.DeleteMapping;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.PutMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class GreetingController {
                  @GetMapping("/greeting")
                  public Response greeting(String name) {
                      String greeting = "Hello, " + name;
                      return Response.ok(greeting).build();
                  }

                  @PutMapping("/greeting")
                  public Response replace(String greeting) {
                      return Response.status(Response.Status.CONFLICT).entity(greeting).build();
                  }

                  @DeleteMapping("/greeting")
                  public Response delete() {
                      return Response.status(Response.Status.NO_CONTENT).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void statusCodesWithoutJaxRsConstant() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class TeapotController {
                  @GetMapping("/teapot")
                  ResponseEntity<String> teapot() {
                      return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).body("short and stout");
                  }

                  @GetMapping("/validate")
                  ResponseEntity<Void> validate() {
                      return ResponseEntity.unprocessableEntity().build();
                  }

                  @GetMapping("/raw")
                  ResponseEntity<Void> raw(int code) {
                      return ResponseEntity.status(code).build();
                  }
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class TeapotController {
                  @GetMapping("/teapot")
                  Response teapot() {
                      return Response.status(418).entity("short and stout").build();
                  }

                  @GetMapping("/validate")
                  Response validate() {
                      return Response.status(422).build();
                  }

                  @GetMapping("/raw")
                  Response raw(int code) {
                      return Response.status(code).build();
                  }
              }
              """
          )
        );
    }

    @Test
    void builderFactoriesAndHeaders() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.MediaType;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.DeleteMapping;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RestController;

              import java.time.Instant;

              @RestController
              class DocumentController {
                  @PostMapping("/accept")
                  ResponseEntity<Void> accept() {
                      return ResponseEntity.accepted().build();
                  }

                  @PostMapping("/validate")
                  ResponseEntity<String> validate() {
                      return ResponseEntity.badRequest().body("invalid");
                  }

                  @DeleteMapping("/document")
                  ResponseEntity<Void> delete() {
                      return ResponseEntity.noContent().header("X-Deleted", "true").build();
                  }

                  @GetMapping("/document")
                  ResponseEntity<String> document(Instant modified, String etag) {
                      return ResponseEntity.ok()
                              .contentType(MediaType.TEXT_PLAIN)
                              .eTag(etag)
                              .lastModified(modified)
                              .body("content");
                  }

                  @GetMapping("/error")
                  ResponseEntity<String> error() {
                      return ResponseEntity.internalServerError().body("oops");
                  }
              }
              """,
            """
              import jakarta.ws.rs.core.MediaType;
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.DeleteMapping;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.PostMapping;
              import org.springframework.web.bind.annotation.RestController;

              import java.time.Instant;
              import java.util.Date;

              @RestController
              class DocumentController {
                  @PostMapping("/accept")
                  Response accept() {
                      return Response.accepted().build();
                  }

                  @PostMapping("/validate")
                  Response validate() {
                      return Response.status(Response.Status.BAD_REQUEST).entity("invalid").build();
                  }

                  @DeleteMapping("/document")
                  Response delete() {
                      return Response.noContent().header("X-Deleted", "true").build();
                  }

                  @GetMapping("/document")
                  Response document(Instant modified, String etag) {
                      return Response.ok()
                              .type(MediaType.TEXT_PLAIN)
                              .tag(etag)
                              .lastModified(Date.from(modified))
                              .entity("content").build();
                  }

                  @GetMapping("/error")
                  Response error() {
                      return Response.serverError().entity("oops").build();
                  }
              }
              """
          )
        );
    }

    @Test
    void reactiveWrapper() {
        rewriteRun(
          //language=java
          java(
            """
              package reactor.core.publisher;

              import java.util.function.Function;

              public abstract class Mono<T> {
                  public abstract <R> Mono<R> map(Function<? super T, ? extends R> mapper);
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;
              import reactor.core.publisher.Mono;

              @RestController
              class MessageController {
                  @GetMapping("/message")
                  Mono<ResponseEntity<String>> message(Mono<String> message) {
                      return message.map(m -> ResponseEntity.ok().body(m));
                  }
              }
              """,
            """
              import jakarta.ws.rs.core.Response;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RestController;
              import reactor.core.publisher.Mono;

              @RestController
              class MessageController {
                  @GetMapping("/message")
                  Mono<Response> message(Mono<String> message) {
                      return message.map(m -> Response.ok().entity(m).build());
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOutsideResourceMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.client.RestTemplate;

              class GitHubTemplate {
                  private final RestTemplate restTemplate = new RestTemplate();

                  ResponseEntity<String> fetch(String url) {
                      ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.http.HttpStatus;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.ControllerAdvice;
              import org.springframework.web.bind.annotation.ExceptionHandler;

              @ControllerAdvice
              class GlobalControllerAdvice {
                  @ExceptionHandler(Throwable.class)
                  public ResponseEntity<String> problem(Throwable e) {
                      return new ResponseEntity<String>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.stereotype.Controller;
              import org.springframework.web.bind.annotation.GetMapping;

              @Controller
              class HomeController {
                  @GetMapping("/")
                  String home() {
                      return "home";
                  }

                  @GetMapping("/ping")
                  ResponseEntity<String> ping() {
                      return ResponseEntity.ok("pong");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeHandlersThatWebToJaxRsLeavesToSpring() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class AnyMethodController {
                  @RequestMapping("/ping")
                  ResponseEntity<String> ping() {
                      return ResponseEntity.ok("pong");
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              @RequestMapping({"/v1", "/v2"})
              class TwoPathController {
                  @GetMapping("/ping")
                  ResponseEntity<String> ping() {
                      return ResponseEntity.ok("pong");
                  }
              }
              """
          )
        );
    }

    @Test
    void markMethodsThatCannotBeConverted() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class DownloadController {
                  @GetMapping("/download/{fileId}")
                  ResponseEntity<byte[]> download(@PathVariable String fileId, byte[] content) {
                      if (content.length == 0) {
                          return ResponseEntity.notFound().build();
                      }
                      HttpHeaders headers = new HttpHeaders();
                      headers.setContentLength(content.length);
                      return ResponseEntity.ok()
                              .headers(headers)
                              .body(content);
                  }

                  @GetMapping("/latest")
                  ResponseEntity<byte[]> latest(byte[] content) {
                      return download("latest", content);
                  }
              }
              """,
            """
              import org.springframework.http.HttpHeaders;
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.RestController;

              @RestController
              class DownloadController {
                  @GetMapping("/download/{fileId}")
                  ResponseEntity<byte[]> download(@PathVariable String fileId, byte[] content) {
                      if (content.length == 0) {
                          return ResponseEntity.notFound().build();
                      }
                      HttpHeaders headers = new HttpHeaders();
                      headers.setContentLength(content.length);
                      return ResponseEntity.ok()
                              ./*~~(`headers(..)` has no JAX-RS equivalent; convert this method to `jakarta.ws.rs.core.Response` manually)~~>*/headers(headers)
                              .body(content);
                  }

                  @GetMapping("/latest")
                  ResponseEntity<byte[]> latest(byte[] content) {
                      return /*~~(This `ResponseEntity` is not built here; convert this method to `jakarta.ws.rs.core.Response` manually)~~>*/download("latest", content);
                  }
              }
              """
          )
        );
    }

    @Test
    void markKotlinResourceMethods() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6", "spring-context-6", "spring-core-6")),
          //language=kotlin
          kotlin(
            """
              import org.springframework.http.ResponseEntity
              import org.springframework.web.bind.annotation.*

              @RestController
              @RequestMapping("/api/v1/messages")
              class MessageResource {
                  @GetMapping
                  fun latest(messages: List<String>): ResponseEntity<List<String>> {
                      return with(ResponseEntity.ok()) {
                          header("lastMessageId", messages.last())
                          body(messages)
                      }
                  }
              }
              """,
            """
              import org.springframework.http.ResponseEntity
              import org.springframework.web.bind.annotation.*

              @RestController
              @RequestMapping("/api/v1/messages")
              class MessageResource {
                  @GetMapping
                  fun latest(messages: List<String>): /*~~(Only Java sources are converted; convert this method to `jakarta.ws.rs.core.Response` manually)~~>*/ResponseEntity<List<String>> {
                      return with(ResponseEntity.ok()) {
                          header("lastMessageId", messages.last())
                          body(messages)
                      }
                  }
              }
              """
          )
        );
    }
}
