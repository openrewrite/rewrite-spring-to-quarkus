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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

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
                new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public @Nullable Tree preVisit(Tree tree, ExecutionContext ctx) {
                        tree = new RestControllerVisitor().visit(tree, ctx);
                        tree = new RequestMappingVisitor().visit(tree, ctx);
                        tree = new HttpMethodMappingVisitor().visit(tree, ctx);
                        tree = new HttpMethodMappingVisitor().visit(tree, ctx);
                        tree = new HttpMethodMappingVisitor().visit(tree, ctx);
                        tree = new HttpMethodMappingVisitor().visit(tree, ctx);
                        tree = new HttpMethodMappingVisitor().visit(tree, ctx);
                        tree = new PathVariableVisitor().visit(tree, ctx);
                        tree = new RequestParamVisitor().visit(tree, ctx);
                        tree = new RequestBodyVisitor().visit(tree, ctx);
                        tree = new ResponseBodyVisitor().visit(tree, ctx);
                        stopAfterPreVisit();
                        return tree;
                    }
                }
        );
    }

    private static class RestControllerVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REST_CONTROLLER_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RestController");
        private static final AnnotationMatcher CONTROLLER_MATCHER = new AnnotationMatcher("@org.springframework.stereotype.Controller");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            boolean hasRestController = false;
            boolean hasResponseBody = false;
            boolean hasController = false;

            for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                if (REST_CONTROLLER_MATCHER.matches(annotation)) {
                    hasRestController = true;
                } else if (CONTROLLER_MATCHER.matches(annotation)) {
                    hasController = true;
                } else if (new AnnotationMatcher("@org.springframework.web.bind.annotation.ResponseBody").matches(annotation)) {
                    hasResponseBody = true;
                }
            }

            if (hasRestController || (hasController && hasResponseBody)) {
                // Remove @RestController, @Controller, and @ResponseBody
                cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), ann -> {
                    if (REST_CONTROLLER_MATCHER.matches(ann) ||
                            CONTROLLER_MATCHER.matches(ann) ||
                            new AnnotationMatcher("@org.springframework.web.bind.annotation.ResponseBody").matches(ann)) {
                        maybeRemoveImport("org.springframework.web.bind.annotation.RestController");
                        maybeRemoveImport("org.springframework.stereotype.Controller");
                        maybeRemoveImport("org.springframework.web.bind.annotation.ResponseBody");
                        return null;
                    }
                    return ann;
                }));

                // Add @Path annotation if class doesn't have @RequestMapping
                boolean hasRequestMapping = cd.getLeadingAnnotations().stream()
                        .anyMatch(ann -> new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestMapping").matches(ann));

                if (!hasRequestMapping) {
                    // Add @Path("") annotation to the class
                    maybeAddImport("jakarta.ws.rs.Path");
                    JavaTemplate template = JavaTemplate.builder("@Path(\"\")")
                            .imports("jakarta.ws.rs.Path")
                            .build();
                    cd = template.apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                }
            }

            return cd;
        }
    }

    private static class RequestMappingVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REQUEST_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestMapping");

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Check if we need to add @Path annotation
            String pathToAdd = getCursor().pollMessage("ADD_PATH_ANNOTATION");
            if (pathToAdd != null) {
                maybeAddImport("jakarta.ws.rs.Path");
                JavaTemplate template = JavaTemplate.builder("@Path(\"" + pathToAdd + "\")")
                        .imports("jakarta.ws.rs.Path")
                        .build();
                m = template.apply(getCursor(), m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

            return m;
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);

            if (REQUEST_MAPPING_MATCHER.matches(ann)) {
                Object parent = getCursor().getParentOrThrow().getValue();

                if (parent instanceof J.ClassDeclaration) {
                    // Class-level @RequestMapping -> @Path
                    return convertRequestMappingToPath(ann, ctx);
                } else if (parent instanceof J.MethodDeclaration) {
                    // Method-level @RequestMapping -> @Path + HTTP method annotation
                    return convertMethodRequestMapping(ann, ctx);
                }
            }

            return ann;
        }

        private J.Annotation convertRequestMappingToPath(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = annotation;
            maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
            maybeAddImport("jakarta.ws.rs.Path");

            // Extract path value from @RequestMapping
            String pathValue = extractPathValue(annotation);

            // Change to @Path
            annotation = (J.Annotation) new ChangeType(
                    "org.springframework.web.bind.annotation.RequestMapping",
                    "jakarta.ws.rs.Path",
                    false
            ).getVisitor().visit(annotation, ctx, getCursor().getParentOrThrow());

            // Update arguments to just the path
            if (annotation != null && pathValue != null) {
                JavaTemplate template = JavaTemplate.builder("#{}")
                        .build();
                annotation = annotation.withArguments(Collections.singletonList(
                        template.apply(getCursor(), annotation.getCoordinates().replaceArguments(),
                                "\"" + pathValue + "\"")
                ));
            }

            return annotation != null ? annotation : ann;
        }

        private J.@Nullable Annotation convertMethodRequestMapping(J.Annotation annotation, ExecutionContext ctx) {
            // Extract method type from @RequestMapping
            String methodType = extractMethodType(annotation);
            String pathValue = extractPathValue(annotation);

            if (methodType != null) {
                String jaxRsAnnotation = getJaxRsMethodAnnotation(methodType);
                if (jaxRsAnnotation != null) {
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod");
                    maybeAddImport("jakarta.ws.rs." + jaxRsAnnotation);

                    // If there was a path, we'll need to handle it in visitMethodDeclaration
                    if (pathValue != null && !pathValue.isEmpty()) {
                        getCursor().getParentOrThrow().putMessage("ADD_PATH_ANNOTATION", pathValue);
                    }

                    // Change to JAX-RS HTTP method annotation
                    annotation = (J.Annotation) new ChangeType(
                            "org.springframework.web.bind.annotation.RequestMapping",
                            "jakarta.ws.rs." + jaxRsAnnotation,
                            false
                    ).getVisitor().visit(annotation, ctx, getCursor().getParentOrThrow());

                    // Remove all arguments for HTTP method annotations
                    if (annotation != null) {
                        annotation = annotation.withArguments(null);
                    }
                }
            }

            return annotation;
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

    private static class HttpMethodMappingVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Check if we need to add @Path annotation
            Set<String> pathKeys = getCursor().getNearestMessage("PATH_KEYS", Collections.<String>emptySet());
            for (String key : pathKeys) {
                if (key.startsWith("ADD_PATH_")) {
                    String pathValue = getCursor().pollMessage(key);
                    if (pathValue != null) {
                        maybeAddImport("jakarta.ws.rs.Path");
                        JavaTemplate template = JavaTemplate.builder("@Path(\"" + pathValue + "\")")
                                .imports("jakarta.ws.rs.Path")
                                .build();
                        m = template.apply(getCursor(), m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    }
                }
            }

            return m;
        }

        @Override
        public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);

            String annotationType = getAnnotationSimpleName(ann);
            if (annotationType == null) {
                return ann;
            }

            String jaxRsAnnotation = null;
            String springAnnotation = null;

            switch (annotationType) {
                case "GetMapping":
                    jaxRsAnnotation = "GET";
                    springAnnotation = "org.springframework.web.bind.annotation.GetMapping";
                    break;
                case "PostMapping":
                    jaxRsAnnotation = "POST";
                    springAnnotation = "org.springframework.web.bind.annotation.PostMapping";
                    break;
                case "PutMapping":
                    jaxRsAnnotation = "PUT";
                    springAnnotation = "org.springframework.web.bind.annotation.PutMapping";
                    break;
                case "DeleteMapping":
                    jaxRsAnnotation = "DELETE";
                    springAnnotation = "org.springframework.web.bind.annotation.DeleteMapping";
                    break;
                case "PatchMapping":
                    jaxRsAnnotation = "PATCH";
                    springAnnotation = "org.springframework.web.bind.annotation.PatchMapping";
                    break;
            }

            if (jaxRsAnnotation != null && springAnnotation != null) {
                // Extract path value
                String pathValue = extractPathValue(ann);

                maybeRemoveImport(springAnnotation);
                maybeAddImport("jakarta.ws.rs." + jaxRsAnnotation);

                // If there was a path, need to add @Path annotation
                if (pathValue != null && !pathValue.isEmpty()) {
                    getCursor().getParentOrThrow().putMessage("ADD_PATH_" + pathValue, pathValue);
                    Set<String> pathKeys = getCursor().getParentOrThrow().getNearestMessage("PATH_KEYS", new HashSet<>());
                    pathKeys.add("ADD_PATH_" + pathValue);
                    getCursor().getParentOrThrow().putMessage("PATH_KEYS", pathKeys);
                }

                // Convert to JAX-RS annotation
                ann = (J.Annotation) new ChangeType(
                        springAnnotation,
                        "jakarta.ws.rs." + jaxRsAnnotation,
                        false
                ).getVisitor().visit(ann, ctx, getCursor().getParentOrThrow());

                // Remove arguments
                if (ann != null) {
                    ann = ann.withArguments(null);
                }
            }

            return ann;
        }

        private @Nullable String getAnnotationSimpleName(J.Annotation annotation) {
            if (annotation.getAnnotationType() instanceof J.Identifier) {
                return ((J.Identifier) annotation.getAnnotationType()).getSimpleName();
            } else if (annotation.getAnnotationType() instanceof J.FieldAccess) {
                return ((J.FieldAccess) annotation.getAnnotationType()).getSimpleName();
            }
            return null;
        }

        private @Nullable String extractPathValue(J.Annotation annotation) {
            if (annotation.getArguments() == null || annotation.getArguments().isEmpty()) {
                return null;
            }

            Expression firstArg = annotation.getArguments().get(0);
            if (firstArg instanceof J.Literal) {
                Object value = ((J.Literal) firstArg).getValue();
                if (value instanceof String) {
                    return (String) value;
                }
            } else if (firstArg instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) firstArg;
                if (assignment.getVariable() instanceof J.Identifier) {
                    String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                    if ("value".equals(name) || "path".equals(name)) {
                        if (assignment.getAssignment() instanceof J.Literal) {
                            Object value = ((J.Literal) assignment.getAssignment()).getValue();
                            if (value instanceof String) {
                                return (String) value;
                            }
                        }
                    }
                }
            }

            return null;
        }
    }

    private static class PathVariableVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher PATH_VARIABLE_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PathVariable");

        @Override
        public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);

            if (PATH_VARIABLE_MATCHER.matches(ann)) {
                maybeRemoveImport("org.springframework.web.bind.annotation.PathVariable");
                maybeAddImport("jakarta.ws.rs.PathParam");

                ann = (J.Annotation) new ChangeType(
                        "org.springframework.web.bind.annotation.PathVariable",
                        "jakarta.ws.rs.PathParam",
                        false
                ).getVisitor().visit(ann, ctx, getCursor().getParentOrThrow());
            }

            return ann;
        }
    }

    private static class RequestParamVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REQUEST_PARAM_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestParam");

        @Override
        public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);

            if (REQUEST_PARAM_MATCHER.matches(ann)) {
                maybeRemoveImport("org.springframework.web.bind.annotation.RequestParam");
                maybeAddImport("jakarta.ws.rs.QueryParam");

                ann = (J.Annotation) new ChangeType(
                        "org.springframework.web.bind.annotation.RequestParam",
                        "jakarta.ws.rs.QueryParam",
                        false
                ).getVisitor().visit(ann, ctx, getCursor().getParentOrThrow());
            }

            return ann;
        }
    }

    private static class RequestBodyVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REQUEST_BODY_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestBody");

        @Override
        public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);

            if (REQUEST_BODY_MATCHER.matches(ann)) {
                maybeRemoveImport("org.springframework.web.bind.annotation.RequestBody");
                // JAX-RS doesn't require annotation for request body (it's implicit)
                return null;
            }

            return ann;
        }
    }

    private static class ResponseBodyVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher RESPONSE_BODY_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.ResponseBody");

        @Override
        public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);

            if (RESPONSE_BODY_MATCHER.matches(ann)) {
                maybeRemoveImport("org.springframework.web.bind.annotation.ResponseBody");
                // JAX-RS doesn't require @ResponseBody (it's implicit for resource methods)
                return null;
            }

            return ann;
        }
    }
}
