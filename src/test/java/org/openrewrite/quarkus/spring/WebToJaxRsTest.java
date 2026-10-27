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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "unused"})
class WebToJaxRsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new WebToJaxRs())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-web-6", "spring-context-6", "spring-core-6"));
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
                  public String updateUser(@PathParam("id") Long id) {
                      return "updated";
                  }

                  @DELETE
                  @Path("/users/{id}")
                  public String deleteUser(@PathParam("id") Long id) {
                      return "deleted";
                  }

                  @PATCH
                  @Path("/users/{id}")
                  public String patchUser(@PathParam("id") Long id) {
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

    @Issue("https://github.com/openrewrite/rewrite-spring-to-quarkus/issues/76")
    @Test
    void convertPathVariableWithoutExplicitValue() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              @RequestMapping("/api")
              public class GreetingController {

                  @GetMapping("/hello/{name}")
                  public String hello(@PathVariable String name) {
                      return "Hello, " + name + "!";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.Path;
              import jakarta.ws.rs.PathParam;

              @Path("/api")
              public class GreetingController {

                  @GET
                  @Path("/hello/{name}")
                  public String hello(@PathParam("name") String name) {
                      return "Hello, " + name + "!";
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring-to-quarkus/issues/76")
    @Test
    void convertPathVariableWithNonValueAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class UserController {

                  @GetMapping("/users/{id}")
                  public String getUser(@PathVariable(required = false) Long id) {
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
                  public String getUser(@PathParam("id") Long id) {
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

    @Issue("https://github.com/openrewrite/rewrite-spring-to-quarkus/issues/76")
    @Test
    void convertRequestParamWithoutExplicitValue() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class UserController {

                  @GetMapping("/users")
                  public String getUsers(@RequestParam int page) {
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
                  public String getUsers(@QueryParam("page") int page) {
                      return "users";
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring-to-quarkus/issues/76")
    @Test
    void convertRequestHeaderWithoutExplicitValue() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.*;

              @RestController
              public class UserController {

                  @GetMapping("/users")
                  public String getUsers(@RequestHeader String authorization) {
                      return "users";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.HeaderParam;
              import jakarta.ws.rs.Path;

              @Path("")
              public class UserController {

                  @GET
                  @Path("/users")
                  public String getUsers(@HeaderParam("authorization") String authorization) {
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
    void convertRequestHeader() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.web.bind.annotation.RestController;
              import org.springframework.web.bind.annotation.GetMapping;
              import org.springframework.web.bind.annotation.RequestHeader;

              @RestController
              public class UserController {
                  @GetMapping("/users")
                  public String getUsers(@RequestHeader("Authorization") String auth) {
                      return "users";
                  }
              }
              """,
            """
              import jakarta.ws.rs.GET;
              import jakarta.ws.rs.HeaderParam;
              import jakarta.ws.rs.Path;

              @Path("")
              public class UserController {
                  @GET
                  @Path("/users")
                  public String getUsers(@HeaderParam("Authorization") String auth) {
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
                  public String getUser(@PathParam("id") Long id) {
                      return "user";
                  }

                  @POST
                  @Path("/users")
                  public String createUser(User user) {
                      return "created";
                  }

                  @PUT
                  @Path("/users/{id}")
                  public String updateUser(@PathParam("id") Long id, User user) {
                      return "updated";
                  }

                  @DELETE
                  @Path("/users/{id}")
                  public String deleteUser(@PathParam("id") Long id) {
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
                  import org.springframework.web.bind.annotation.RequestMethod;
                  import org.springframework.web.bind.annotation.RestController;

                  @RestController
                  public class TestController {

                      @RequestMapping(value = "/test", method = RequestMethod.GET, consumes = "application/json", produces = "application/xml")
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
                  import org.springframework.web.bind.annotation.RequestMethod;
                  import org.springframework.web.bind.annotation.RestController;

                  @RestController
                  public class TestController {

                      @RequestMapping(value = "/test", method = RequestMethod.GET, consumes = {"application/json", "application/xml"})
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

    @Nested
    class ParameterBinding {
        @Test
        void keepDefaultValue() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  public class GreetingController {

                      @GetMapping("/greeting")
                      public String greeting(@RequestParam(defaultValue = "World") String name,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestHeader(value = "X-Locale", defaultValue = "en") String locale,
                                             @CookieValue(name = "theme", required = false, defaultValue = "light") String theme) {
                          return "Hello, " + name;
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.*;

                  @Path("")
                  public class GreetingController {

                      @GET
                      @Path("/greeting")
                      public String greeting(@QueryParam("name") @DefaultValue("World") String name,
                                             @QueryParam("page") @DefaultValue("1") int page,
                                             @HeaderParam("X-Locale") @DefaultValue("en") String locale,
                                             @CookieParam("theme") @DefaultValue("light") String theme) {
                          return "Hello, " + name;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void boxOptionalPrimitiveWithoutDefault() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  public class VetController {

                      @GetMapping("/vets")
                      public String vets(@RequestParam(required = false) int page,
                                         @RequestParam(value = "accepts_incomplete", required = false) boolean acceptsIncomplete) {
                          return "vets";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;
                  import jakarta.ws.rs.QueryParam;

                  @Path("")
                  public class VetController {

                      @GET
                      @Path("/vets")
                      public String vets(@QueryParam("page") Integer page,
                                         @QueryParam("accepts_incomplete") boolean acceptsIncomplete) {
                          return "vets";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void constantParameterName() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  public class ServiceInstanceController {
                      static final String INSTANCE_ID = "instanceId";

                      @GetMapping("/instances/{instanceId}")
                      public String get(@PathVariable(INSTANCE_ID) String serviceInstanceId) {
                          return serviceInstanceId;
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;
                  import jakarta.ws.rs.PathParam;

                  @Path("")
                  public class ServiceInstanceController {
                      static final String INSTANCE_ID = "instanceId";

                      @GET
                      @Path("/instances/{instanceId}")
                      public String get(@PathParam(INSTANCE_ID) String serviceInstanceId) {
                          return serviceInstanceId;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeMultipartUpload() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;
                  import org.springframework.web.multipart.MultipartFile;

                  @RestController
                  public class FileUploadController {

                      @PostMapping("/")
                      public String handleFileUpload(@RequestParam("file") MultipartFile file) {
                          return file.getOriginalFilename();
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.Path;
                  import org.springframework.web.bind.annotation.PostMapping;
                  import org.springframework.web.bind.annotation.RequestParam;
                  import org.springframework.web.multipart.MultipartFile;

                  @Path("")
                  public class FileUploadController {

                      /*~~(Migrate `MultipartFile` to `@RestForm FileUpload` manually)~~>*/@PostMapping("/")
                      public String handleFileUpload(@RequestParam("file") MultipartFile file) {
                          return file.getOriginalFilename();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeHeaderAndParameterMaps() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Map;
                  import org.springframework.http.HttpHeaders;
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  public class CatalogController {

                      @GetMapping("/catalog")
                      public String catalog(@RequestHeader HttpHeaders httpHeaders) {
                          return httpHeaders.getFirst("X-Api-Version");
                      }

                      @GetMapping("/search")
                      public String search(@RequestParam Map<String, String> parameters) {
                          return parameters.get("q");
                      }
                  }
                  """,
                """
                  import java.util.Map;

                  import jakarta.ws.rs.Path;
                  import org.springframework.http.HttpHeaders;
                  import org.springframework.web.bind.annotation.GetMapping;
                  import org.springframework.web.bind.annotation.RequestHeader;
                  import org.springframework.web.bind.annotation.RequestParam;

                  @Path("")
                  public class CatalogController {

                      /*~~(Binds all values; migrate to `@Context HttpHeaders` or `@Context UriInfo` manually)~~>*/@GetMapping("/catalog")
                      public String catalog(@RequestHeader HttpHeaders httpHeaders) {
                          return httpHeaders.getFirst("X-Api-Version");
                      }

                      /*~~(Binds all values; migrate to `@Context HttpHeaders` or `@Context UriInfo` manually)~~>*/@GetMapping("/search")
                      public String search(@RequestParam Map<String, String> parameters) {
                          return parameters.get("q");
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class HttpMethods {
        @Test
        void singleElementMethodArray() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  public class UserController {

                      @RequestMapping(value = "/users", method = {RequestMethod.PUT})
                      public String update() {
                          return "updated";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.PUT;
                  import jakarta.ws.rs.Path;

                  @Path("")
                  public class UserController {

                      @PUT
                      @Path("/users")
                      public String update() {
                          return "updated";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeRequestMappingWithoutSingleMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  public class GreetingController {

                      @RequestMapping("/greeting")
                      public String greeting() {
                          return "Hello World";
                      }

                      @RequestMapping(value = "/users", method = {RequestMethod.GET, RequestMethod.POST})
                      public String users() {
                          return "users";
                      }

                      @RequestMapping(value = "/trace", method = RequestMethod.TRACE)
                      public String trace() {
                          return "trace";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.Path;
                  import org.springframework.web.bind.annotation.RequestMapping;
                  import org.springframework.web.bind.annotation.RequestMethod;

                  @Path("")
                  public class GreetingController {

                      /*~~(Spring maps every HTTP method here; add the JAX-RS HTTP method annotation manually)~~>*/@RequestMapping("/greeting")
                      public String greeting() {
                          return "Hello World";
                      }

                      /*~~(JAX-RS needs a separate resource method per HTTP method)~~>*/@RequestMapping(value = "/users", method = {RequestMethod.GET, RequestMethod.POST})
                      public String users() {
                          return "users";
                      }

                      /*~~(JAX-RS has no `TRACE` HTTP method annotation)~~>*/@RequestMapping(value = "/trace", method = RequestMethod.TRACE)
                      public String trace() {
                          return "trace";
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Paths {
        @Test
        void unwrapSingleElementPathArray() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  class VetController {

                      @GetMapping({ "/vets" })
                      public String showResourcesVetList() {
                          return "vets";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;

                  @Path("")
                  class VetController {

                      @GET
                      @Path("/vets")
                      public String showResourcesVetList() {
                          return "vets";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void constantAndConcatenatedPaths() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  @RequestMapping(Paths.BASE)
                  class MessageController {

                      @GetMapping(Paths.MESSAGES)
                      public String messages() {
                          return "messages";
                      }

                      @GetMapping(Paths.MESSAGES + "/{id}")
                      public String message(@PathVariable String id) {
                          return id;
                      }
                  }

                  class Paths {
                      static final String BASE = "/api";
                      static final String MESSAGES = "/messages";
                  }
                  """,
                """
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;
                  import jakarta.ws.rs.PathParam;

                  @Path(Paths.BASE)
                  class MessageController {

                      @GET
                      @Path(Paths.MESSAGES)
                      public String messages() {
                          return "messages";
                      }

                      @GET
                      @Path(Paths.MESSAGES + "/{id}")
                      public String message(@PathParam("id") String id) {
                          return id;
                      }
                  }

                  class Paths {
                      static final String BASE = "/api";
                      static final String MESSAGES = "/messages";
                  }
                  """
              )
            );
        }

        @Test
        void bareClassLevelRequestMapping() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  @RequestMapping
                  public class HelloController {

                      @GetMapping("/hello")
                      public String hello() {
                          return "hello";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;

                  @Path("")
                  public class HelloController {

                      @GET
                      @Path("/hello")
                      public String hello() {
                          return "hello";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeMultiplePaths() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  public class OAuth2WebClientController {

                      @GetMapping(path = { "/webclient", "/public/webclient" })
                      public String index() {
                          return "index";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.Path;
                  import org.springframework.web.bind.annotation.GetMapping;

                  @Path("")
                  public class OAuth2WebClientController {

                      /*~~(JAX-RS `@Path` supports a single path)~~>*/@GetMapping(path = { "/webclient", "/public/webclient" })
                      public String index() {
                          return "index";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeMultipleClassLevelPaths() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  @RequestMapping({ "/v1", "/v2" })
                  public class UserController {

                      @GetMapping("/users")
                      public String users() {
                          return "users";
                      }
                  }
                  """,
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  /*~~(JAX-RS `@Path` supports a single path)~~>*/@RequestMapping({ "/v1", "/v2" })
                  public class UserController {

                      @GetMapping("/users")
                      public String users() {
                          return "users";
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class Controllers {
        @Test
        void controllerWithResponseBodyOnEveryHandler() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.stereotype.Controller;
                  import org.springframework.web.bind.annotation.GetMapping;
                  import org.springframework.web.bind.annotation.ResponseBody;

                  @Controller
                  public class GreetingController {

                      @GetMapping("/greeting")
                      public @ResponseBody String greeting() {
                          return "Hello World";
                      }

                      @ResponseBody
                      @GetMapping("/farewell")
                      public String farewell() {
                          return "Goodbye";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;

                  @Path("")
                  public class GreetingController {

                      @GET
                      @Path("/greeting")
                      public String greeting() {
                          return "Hello World";
                      }

                      @GET
                      @Path("/farewell")
                      public String farewell() {
                          return "Goodbye";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void removeResponseBodyFromRestController() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RestController
                  public class GreetingController {

                      @ResponseBody
                      @GetMapping("/greeting")
                      public String greeting() {
                          return "Hello World";
                      }
                  }
                  """,
                """
                  import jakarta.ws.rs.GET;
                  import jakarta.ws.rs.Path;

                  @Path("")
                  public class GreetingController {

                      @GET
                      @Path("/greeting")
                      public String greeting() {
                          return "Hello World";
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeViewController() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.stereotype.Controller;
                  import org.springframework.ui.Model;
                  import org.springframework.web.bind.annotation.*;

                  @Controller
                  public class GreetingController {

                      @GetMapping("/")
                      public String welcome() {
                          return "welcome";
                      }

                      @GetMapping("/greeting")
                      public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
                          model.addAttribute("name", name);
                          return "greeting";
                      }

                      @PostMapping("/greeting")
                      public String greetingSubmit(@ModelAttribute Greeting greeting, @RequestBody String body) {
                          return "result";
                      }

                      @GetMapping({ "/vets" })
                      public @ResponseBody Greeting vets() {
                          return new Greeting();
                      }
                  }

                  class Greeting {
                  }
                  """,
                """
                  import org.springframework.stereotype.Controller;
                  import org.springframework.ui.Model;
                  import org.springframework.web.bind.annotation.*;

                  /*~~(Spring MVC view controller; migrate to Qute templates manually)~~>*/@Controller
                  public class GreetingController {

                      @GetMapping("/")
                      public String welcome() {
                          return "welcome";
                      }

                      @GetMapping("/greeting")
                      public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
                          model.addAttribute("name", name);
                          return "greeting";
                      }

                      @PostMapping("/greeting")
                      public String greetingSubmit(@ModelAttribute Greeting greeting, @RequestBody String body) {
                          return "result";
                      }

                      @GetMapping({ "/vets" })
                      public @ResponseBody Greeting vets() {
                          return new Greeting();
                      }
                  }

                  class Greeting {
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeFeignClient() {
            rewriteRun(
              java(
                """
                  package org.springframework.cloud.openfeign;

                  public @interface FeignClient {
                      String value() default "";
                  }
                  """
              ),
              //language=java
              java(
                """
                  import org.springframework.cloud.openfeign.FeignClient;
                  import org.springframework.web.bind.annotation.*;

                  @FeignClient("example")
                  public interface ExampleClient {

                      @RequestMapping(value = "/", method = RequestMethod.POST)
                      String example(@RequestBody String body);
                  }
                  """
              )
            );
        }

        @Test
        void convertApiInterface() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.springframework.web.bind.annotation.*;

                  @RequestMapping("/users")
                  public interface UserApi {

                      @PostMapping
                      String createUser(@RequestBody String user);
                  }
                  """,
                """
                  import jakarta.ws.rs.POST;
                  import jakarta.ws.rs.Path;

                  @Path("/users")
                  public interface UserApi {

                      @POST
                      String createUser(String user);
                  }
                  """
              )
            );
        }
    }
}
