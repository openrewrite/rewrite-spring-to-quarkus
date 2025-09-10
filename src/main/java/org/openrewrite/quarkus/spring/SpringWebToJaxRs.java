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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Collections;

public class SpringWebToJaxRs extends Recipe {
    @Override
    public String getDisplayName() {
        return "Convert Spring Web annotations to JAX-RS";
    }

    @Override
    public String getDescription() {
        return "Converts Spring Web annotations such as `@RestController`, `@RequestMapping`, `@GetMapping`, etc., to their JAX-RS equivalents like `@Path`, `@GET`, etc.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("org.springframework.web.bind.annotation.RestController", false),
                        new UsesType<>("org.springframework.stereotype.Controller", false),
                        new UsesType<>("org.springframework.web.bind.annotation.RequestMapping", false),
                        new UsesType<>("org.springframework.web.bind.annotation.GetMapping", false),
                        new UsesType<>("org.springframework.web.bind.annotation.PostMapping", false),
                        new UsesType<>("org.springframework.web.bind.annotation.PutMapping", false),
                        new UsesType<>("org.springframework.web.bind.annotation.DeleteMapping", false),
                        new UsesType<>("org.springframework.web.bind.annotation.PatchMapping", false),
                        new UsesType<>("org.springframework.web.bind.annotation.PathVariable", false),
                        new UsesType<>("org.springframework.web.bind.annotation.RequestParam", false),
                        new UsesType<>("org.springframework.web.bind.annotation.RequestBody", false),
                        new UsesType<>("org.springframework.web.bind.annotation.ResponseBody", false)
                ),
                // TODO See if we can avoid repeatUntilStable by making the visitor idempotent in a single pass
                Repeat.repeatUntilStable(new SpringWebToJaxRsVisitor())
        );
    }

    private static class SpringWebToJaxRsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REST_CONTROLLER_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RestController");
        private static final AnnotationMatcher CONTROLLER_MATCHER = new AnnotationMatcher("@org.springframework.stereotype.Controller");
        private static final AnnotationMatcher RESPONSE_BODY_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.ResponseBody");
        private static final AnnotationMatcher REQUEST_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestMapping");
        private static final AnnotationMatcher PATH_VARIABLE_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PathVariable");
        private static final AnnotationMatcher REQUEST_PARAM_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestParam");
        private static final AnnotationMatcher REQUEST_BODY_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestBody");

        // HTTP method matchers
        private static final AnnotationMatcher GET_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.GetMapping");
        private static final AnnotationMatcher POST_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PostMapping");
        private static final AnnotationMatcher PUT_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PutMapping");
        private static final AnnotationMatcher DELETE_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.DeleteMapping");
        private static final AnnotationMatcher PATCH_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PatchMapping");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            // Check if we need to add @Path annotation
            boolean hasRestController = false;
            boolean hasController = false;
            boolean hasResponseBody = false;
            boolean hasPath = false;
            boolean hasRequestMapping = false;

            for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                if (REST_CONTROLLER_MATCHER.matches(annotation)) {
                    hasRestController = true;
                } else if (CONTROLLER_MATCHER.matches(annotation)) {
                    hasController = true;
                } else if (RESPONSE_BODY_MATCHER.matches(annotation)) {
                    hasResponseBody = true;
                } else if (REQUEST_MAPPING_MATCHER.matches(annotation)) {
                    hasRequestMapping = true;
                } else if (annotation.getSimpleName().equals("Path")) {
                    hasPath = true;
                }
            }

            // Add @Path("") only if:
            // 1. We have @RestController or (@Controller + @ResponseBody)
            // 2. We don't have @RequestMapping (which will be converted to @Path)
            // 3. We don't already have @Path
            if ((hasRestController || (hasController && hasResponseBody)) && !hasRequestMapping && !hasPath) {
                maybeAddImport("jakarta.ws.rs.Path");
                JavaTemplate template = JavaTemplate.builder("@Path(\"\")")
                        .imports("jakarta.ws.rs.Path")
                        .build();
                return template.apply(getCursor(), cd.getCoordinates().addAnnotation((a1, a2) -> 0));
            }

            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            // Extract path BEFORE calling super (which will transform the annotations)
            String pathToAdd = null;
            boolean hasHttpMethod = false;

            // Check what the original method has before transformation
            for (J.Annotation annotation : method.getLeadingAnnotations()) {
                if (GET_MAPPING_MATCHER.matches(annotation) || POST_MAPPING_MATCHER.matches(annotation) ||
                    PUT_MAPPING_MATCHER.matches(annotation) || DELETE_MAPPING_MATCHER.matches(annotation) ||
                    PATCH_MAPPING_MATCHER.matches(annotation)) {
                    String path = extractPathValue(annotation);
                    if (path != null && !path.isEmpty()) {
                        pathToAdd = path;
                    }
                    hasHttpMethod = true;
                } else if (REQUEST_MAPPING_MATCHER.matches(annotation)) {
                    String path = extractPathValue(annotation);
                    if (path != null && !path.isEmpty()) {
                        pathToAdd = path;
                    }
                    hasHttpMethod = true;
                }
            }

            // Now transform the annotations
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Check if @Path already exists after transformation
            boolean hasPath = false;
            for (J.Annotation annotation : m.getLeadingAnnotations()) {
                if (annotation.getSimpleName().equals("Path")) {
                    hasPath = true;
                    break;
                }
            }

            // Add @Path if needed
            if (hasHttpMethod && pathToAdd != null && !hasPath) {
                maybeAddImport("jakarta.ws.rs.Path");
                JavaTemplate template = JavaTemplate.builder("@Path(\"" + pathToAdd + "\")")
                        .imports("jakarta.ws.rs.Path")
                        .build();
                m = template.apply(getCursor(), m.getCoordinates().addAnnotation((a1, a2) -> 0));
            }

            return m;
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = annotation;
            Object parent = getCursor().getParentOrThrow().getValue();

            // Handle class-level annotations
            if (parent instanceof J.ClassDeclaration) {
                if (REST_CONTROLLER_MATCHER.matches(ann)) {
                    maybeRemoveImport("org.springframework.web.bind.annotation.RestController");
                    return null; // Remove the annotation
                } else if (CONTROLLER_MATCHER.matches(ann)) {
                    // Check if class also has @ResponseBody
                    J.ClassDeclaration cd = (J.ClassDeclaration) parent;
                    boolean hasResponseBody = cd.getLeadingAnnotations().stream()
                            .anyMatch(RESPONSE_BODY_MATCHER::matches);
                    if (hasResponseBody) {
                        maybeRemoveImport("org.springframework.stereotype.Controller");
                        return null; // Remove the annotation
                    }
                } else if (RESPONSE_BODY_MATCHER.matches(ann)) {
                    // Check if class also has @Controller
                    J.ClassDeclaration cd = (J.ClassDeclaration) parent;
                    boolean hasController = cd.getLeadingAnnotations().stream()
                            .anyMatch(CONTROLLER_MATCHER::matches);
                    if (hasController) {
                        maybeRemoveImport("org.springframework.web.bind.annotation.ResponseBody");
                        return null; // Remove the annotation
                    }
                } else if (REQUEST_MAPPING_MATCHER.matches(ann)) {
                    // Convert @RequestMapping to @Path at class level
                    String path = extractPathValue(ann);
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
                    maybeAddImport("jakarta.ws.rs.Path");

                    JavaTemplate pathTemplate = JavaTemplate.builder("@Path")
                            .imports("jakarta.ws.rs.Path")
                            .build();
                    ann = pathTemplate.apply(getCursor(), ann.getCoordinates().replace());

                    // Simplify arguments to just the path value
                    if (ann != null) {
                        if (path != null) {
                            JavaTemplate template = JavaTemplate.builder("\"" + path + "\"")
                                    .build();
                            ann = ann.withArguments(Collections.singletonList(
                                    template.apply(getCursor(), ann.getCoordinates().replaceArguments())
                            ));
                        } else {
                            ann = ann.withArguments(null);
                        }
                    }
                    return ann;
                }
            }

            // Handle method-level annotations
            if (parent instanceof J.MethodDeclaration) {
                if (REQUEST_MAPPING_MATCHER.matches(ann)) {
                    String methodType = extractMethodType(ann);
                    String jaxRsAnnotation = getJaxRsMethodAnnotation(methodType);

                    if (jaxRsAnnotation != null) {
                        maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
                        maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod");
                        maybeAddImport("jakarta.ws.rs." + jaxRsAnnotation);

                        JavaTemplate methodTemplate = JavaTemplate.builder("@" + jaxRsAnnotation)
                                .imports("jakarta.ws.rs." + jaxRsAnnotation)
                                .build();
                        ann = methodTemplate.apply(getCursor(), ann.getCoordinates().replace());

                        if (ann != null) {
                            ann = ann.withArguments(null);
                        }
                        return ann;
                    }
                } else if (GET_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "GetMapping", "GET", ctx);
                } else if (POST_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "PostMapping", "POST", ctx);
                } else if (PUT_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "PutMapping", "PUT", ctx);
                } else if (DELETE_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "DeleteMapping", "DELETE", ctx);
                } else if (PATCH_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "PatchMapping", "PATCH", ctx);
                }
            }

            // Handle parameter annotations
            if (parent instanceof J.VariableDeclarations) {
                if (PATH_VARIABLE_MATCHER.matches(ann)) {
                    maybeRemoveImport("org.springframework.web.bind.annotation.PathVariable");
                    maybeAddImport("jakarta.ws.rs.PathParam");

                    // Keep arguments if present
                    String newAnn = "@PathParam";
                    if (ann.getArguments() != null && !ann.getArguments().isEmpty()) {
                        // Extract the argument value
                        String args = ann.getArguments().stream()
                                .map(arg -> arg.printTrimmed(getCursor()))
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                        newAnn = "@PathParam(" + args + ")";
                    }
                    JavaTemplate paramTemplate = JavaTemplate.builder(newAnn)
                            .imports("jakarta.ws.rs.PathParam")
                            .build();
                    ann = paramTemplate.apply(getCursor(), ann.getCoordinates().replace());
                    return ann;
                } else if (REQUEST_PARAM_MATCHER.matches(ann)) {
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestParam");
                    maybeAddImport("jakarta.ws.rs.QueryParam");

                    // Keep arguments if present
                    String newAnn = "@QueryParam";
                    if (ann.getArguments() != null && !ann.getArguments().isEmpty()) {
                        // Extract the argument value
                        String args = ann.getArguments().stream()
                                .map(arg -> arg.printTrimmed(getCursor()))
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                        newAnn = "@QueryParam(" + args + ")";
                    }
                    JavaTemplate queryTemplate = JavaTemplate.builder(newAnn)
                            .imports("jakarta.ws.rs.QueryParam")
                            .build();
                    ann = queryTemplate.apply(getCursor(), ann.getCoordinates().replace());
                    return ann;
                } else if (REQUEST_BODY_MATCHER.matches(ann)) {
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestBody");
                    return null; // JAX-RS doesn't require annotation for request body
                }
            }

            return ann;
        }

        private J.@Nullable Annotation convertHttpMethodMapping(J.Annotation ann, String springMapping, String jaxRsMethod, ExecutionContext ctx) {
            maybeRemoveImport("org.springframework.web.bind.annotation." + springMapping);
            maybeAddImport("jakarta.ws.rs." + jaxRsMethod);

            // Build the replacement annotation using JavaTemplate
            JavaTemplate template = JavaTemplate.builder("@" + jaxRsMethod)
                    .imports("jakarta.ws.rs." + jaxRsMethod)
                    .build();

            // Apply the template to replace the annotation
            return template.apply(getCursor(), ann.getCoordinates().replace());
        }

        private @Nullable String extractPathValue(J.Annotation annotation) {
            if (annotation.getArguments() == null) {
                return null;
            }

            for (Expression arg : annotation.getArguments()) {
                if (arg instanceof J.Literal) {
                    J.Literal literal = (J.Literal) arg;
                    if (literal.getValue() instanceof String) {
                        return (String) literal.getValue();
                    }
                } else if (arg instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) arg;
                    if (assignment.getVariable() instanceof J.Identifier) {
                        String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                        if ("value".equals(name) || "path".equals(name)) {
                            if (assignment.getAssignment() instanceof J.Literal) {
                                Object value = ((J.Literal) assignment.getAssignment()).getValue();
                                if (value instanceof String) {
                                    return (String) value;
                                }
                            } else if (assignment.getAssignment() instanceof J.NewArray) {
                                J.NewArray array = (J.NewArray) assignment.getAssignment();
                                if (array.getInitializer() != null && !array.getInitializer().isEmpty()) {
                                    Expression first = array.getInitializer().get(0);
                                    if (first instanceof J.Literal) {
                                        Object value = ((J.Literal) first).getValue();
                                        if (value instanceof String) {
                                            return (String) value;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return null;
        }

        private String extractMethodType(J.Annotation annotation) {
            if (annotation.getArguments() == null) {
                return "GET"; // Default to GET if no method specified
            }

            for (Expression arg : annotation.getArguments()) {
                if (arg instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) arg;
                    if (assignment.getVariable() instanceof J.Identifier &&
                            "method".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                        Expression methodExpr = assignment.getAssignment();
                        if (methodExpr instanceof J.FieldAccess) {
                            return ((J.FieldAccess) methodExpr).getSimpleName();
                        } else if (methodExpr instanceof J.Identifier) {
                            return ((J.Identifier) methodExpr).getSimpleName();
                        }
                    }
                }
            }

            return "GET"; // Default to GET if no method found
        }

        private @Nullable String getJaxRsMethodAnnotation(String springMethod) {
            switch (springMethod) {
                case "GET":
                    return "GET";
                case "POST":
                    return "POST";
                case "PUT":
                    return "PUT";
                case "DELETE":
                    return "DELETE";
                case "PATCH":
                    return "PATCH";
                case "HEAD":
                    return "HEAD";
                case "OPTIONS":
                    return "OPTIONS";
                default:
                    return null;
            }
        }
    }
}
