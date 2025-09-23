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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "unused"})
class SpringWebToJaxRsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpringWebToJaxRs())
          .parser(JavaParser.fromJavaVersion().classpath("spring-web", "spring-context"));
    }

    @DocumentExample
    @Test
    void convertRestController() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.RestController;
              import org.springframework.web.bind.annotation.GetMapping;

              @RestController
              public class UserController {

                  @GetMapping("/users")
                  public String getUsers() {
                      return "users";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.Path;

              @Path("")
              public class UserController {

                  @GET
                  @Path("/users")
                  public String getUsers() {
                      return "users";
                  }
              }
              """
          )
        );
    }

    @Test
    void convertRestControllerWithRequestMapping() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.RestController;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.GetMapping;

              @RestController
              @RequestMapping("/api")
              public class UserController {

                  @GetMapping("/users")
                  public String getUsers() {
                      return "users";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.Path;

              @Path("/api")
              public class UserController {

                  @GET
                  @Path("/users")
                  public String getUsers() {
                      return "users";
                  }
              }
              """
          )
        );
    }

    @Test
    void convertRequestMappingWithMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.RestController;
              import org.springframework.web.bind.annotation.RequestMapping;
              import org.springframework.web.bind.annotation.RequestMethod;

              @RestController
              public class UserController {

                  @RequestMapping(value = "/users", method = RequestMethod.POST)
                  public String createUser() {
                      return "created";
                  }
              }
              """,
            """
              import jakarta.ws.rs.POST;
              import jakarta.ws.rs.Path;

              @Path("")
              public class UserController {

                  @POST
                  @Path("/users")
                  public String createUser() {
                      return "created";
                  }
              }
              """
          )
        );
    }

    @Test
    void convertHttpMethodMappings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class UserController {

                  @GetMapping("/users")
                  public String getUsers() {
                      return "users";
                  }

                  @PostMapping("/users")
                  public String createUser() {
                      return "created";
                  }

                  @PutMapping("/users/{id}")
                  public String updateUser(@PathVariable Long id) {
                      return "updated";
                  }

                  @DeleteMapping("/users/{id}")
                  public String deleteUser(@PathVariable Long id) {
                      return "deleted";
                  }

                  @PatchMapping("/users/{id}")
                  public String patchUser(@PathVariable Long id) {
                      return "patched";
                  }
              }
              """,
            """
              import jakarta.ws.rs.*;

              @Path("")
              public class UserController {

                  @GET
                  @Path("/users")
                  public String getUsers() {
                      return "users";
                  }

                  @POST
                  @Path("/users")
                  public String createUser() {
                      return "created";
                  }

                  @PUT
                  @Path("/users/{id}")
                  public String updateUser(@PathParam Long id) {
                      return "updated";
                  }

                  @DELETE
                  @Path("/users/{id}")
                  public String deleteUser(@PathParam Long id) {
                      return "deleted";
                  }

                  @PATCH
                  @Path("/users/{id}")
                  public String patchUser(@PathParam Long id) {
                      return "patched";
                  }
              }
              """
          )
        );
    }

    @Test
    void convertPathVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class UserController {

                  @GetMapping("/users/{id}")
                  public String getUser(@PathVariable("id") Long userId) {
                      return "user";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.Path;
              import jakarta.ws.rs.PathParam;

              @Path("")
              public class UserController {

                  @GET
                  @Path("/users/{id}")
                  public String getUser(@PathParam("id") Long userId) {
                      return "user";
                  }
              }
              """
          )
        );
    }

    @Test
    void convertRequestParam() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class UserController {

                  @GetMapping("/users")
                  public String getUsers(@RequestParam("page") int page,
                                        @RequestParam("size") int size) {
                      return "users";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.Path;
              import jakarta.ws.rs.QueryParam;

              @Path("")
              public class UserController {

                  @GET
                  @Path("/users")
                  public String getUsers(@QueryParam("page") int page,
                                        @QueryParam("size") int size) {
                      return "users";
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRequestBody() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class UserController {

                  @PostMapping("/users")
                  public String createUser(@RequestBody User user) {
                      return "created";
                  }
              }

              class User {
                  String name;
              }
              """,
            """
              import jakarta.ws.rs.POST;
              import jakarta.ws.rs.Path;

              @Path("")
              public class UserController {

                  @POST
                  @Path("/users")
                  public String createUser(User user) {
                      return "created";
                  }
              }

              class User {
                  String name;
              }
              """
          )
        );
    }

    @Test
    void convertControllerWithResponseBody() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.stereotype.Controller;
              import org.springframework.web.bind.annotation.*;

              @Controller
              @ResponseBody
              public class UserController {

                  @GetMapping("/users")
                  public String getUsers() {
                      return "users";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.Path;

              @Path("")
              public class UserController {

                  @GET
                  @Path("/users")
                  public String getUsers() {
                      return "users";
                  }
              }
              """
          )
        );
    }

    @Test
    void convertComplexExample() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/api/v1")
              public class UserController {

                  @GetMapping("/users")
                  public String listUsers(@RequestParam("page") int page) {
                      return "users";
                  }

                  @GetMapping("/users/{id}")
                  public String getUser(@PathVariable Long id) {
                      return "user";
                  }

                  @PostMapping("/users")
                  public String createUser(@RequestBody User user) {
                      return "created";
                  }

                  @PutMapping("/users/{id}")
                  public String updateUser(@PathVariable Long id, @RequestBody User user) {
                      return "updated";
                  }

                  @DeleteMapping("/users/{id}")
                  public String deleteUser(@PathVariable Long id) {
                      return "deleted";
                  }
              }

              class User {
                  String name;
              }
              """,
            """
              import jakarta.ws.rs.*;

              @Path("/api/v1")
              public class UserController {

                  @GET
                  @Path("/users")
                  public String listUsers(@QueryParam("page") int page) {
                      return "users";
                  }

                  @GET
                  @Path("/users/{id}")
                  public String getUser(@PathParam Long id) {
                      return "user";
                  }

                  @POST
                  @Path("/users")
                  public String createUser(User user) {
                      return "created";
                  }

                  @PUT
                  @Path("/users/{id}")
                  public String updateUser(@PathParam Long id, User user) {
                      return "updated";
                  }

                  @DELETE
                  @Path("/users/{id}")
                  public String deleteUser(@PathParam Long id) {
                      return "deleted";
                  }
              }

              class User {
                  String name;
              }
              """
          )
        );
    }

    @Nested
    class ConsumesProduces {
        @Test
        void convertRequestMappingWithConsumesAndProduces() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.RequestMapping;
                  import org.springframework.web.bind.annotation.RestController;

                  @RestController
                  public class TestController {

                      @RequestMapping(value = "/test", consumes = "application/json", produces = "application/xml")
                      public String test() {
                          return "test";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.Consumes;
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;
                  import jakarta.ws.rs.Produces;

                  @Path("")
                  public class TestController {

                      @GET
                      @Path("/test")
                      @Consumes("application/json")
                      @Produces("application/xml")
                      public String test() {
                          return "test";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void convertGetMappingWithConsumes() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.GetMapping;
                  import org.springframework.web.bind.annotation.RestController;

                  @RestController
                  public class TestController {

                      @GetMapping(value = "/test", consumes = "application/json")
                      public String test() {
                          return "test";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.Consumes;
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;

                  @Path("")
                  public class TestController {

                      @GET
                      @Path("/test")
                      @Consumes("application/json")
                      public String test() {
                          return "test";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void convertPostMappingWithProduces() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.PostMapping;
                  import org.springframework.web.bind.annotation.RestController;

                  @RestController
                  public class TestController {

                      @PostMapping(value = "/test", produces = "application/json")
                      public String test() {
                          return "test";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.POST;
                  import jakarta.ws.rs.Path;
                  import jakarta.ws.rs.Produces;

                  @Path("")
                  public class TestController {

                      @POST
                      @Path("/test")
                      @Produces("application/json")
                      public String test() {
                          return "test";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void convertRequestMappingWithArrayConsumes() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.RequestMapping;
                  import org.springframework.web.bind.annotation.RestController;

                  @RestController
                  public class TestController {

                      @RequestMapping(value = "/test", consumes = {"application/json", "application/xml"})
                      public String test() {
                          return "test";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.Consumes;
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;

                  @Path("")
                  public class TestController {

                      @GET
                      @Path("/test")
                      @Consumes({"application/json", "application/xml"})
                      public String test() {
                          return "test";
                      }
                  }
                  """
              )
            );
        }
    }
}
