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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class WebToJaxRs extends Recipe {
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
                        new UsesType<>("org.springframework.web.bind.annotation.RequestHeader", false),
                        new UsesType<>("org.springframework.web.bind.annotation.RequestBody", false),
                        new UsesType<>("org.springframework.web.bind.annotation.ResponseBody", false)
                ),
                // XXX See if we can avoid repeatUntilStable by making the visitor idempotent in a single pass
                Repeat.repeatUntilStable(new WebToJaxRsVisitor())
        );
    }

    private static class WebToJaxRsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REST_CONTROLLER_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RestController");
        private static final AnnotationMatcher CONTROLLER_MATCHER = new AnnotationMatcher("@org.springframework.stereotype.Controller");
        private static final AnnotationMatcher RESPONSE_BODY_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.ResponseBody");
        private static final AnnotationMatcher REQUEST_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestMapping");
        private static final AnnotationMatcher PATH_VARIABLE_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PathVariable");
        private static final AnnotationMatcher REQUEST_PARAM_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestParam");
        private static final AnnotationMatcher REQUEST_HEADER_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestHeader");
        private static final AnnotationMatcher REQUEST_BODY_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestBody");

        // HTTP method matchers
        private static final AnnotationMatcher GET_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.GetMapping");
        private static final AnnotationMatcher POST_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PostMapping");
        private static final AnnotationMatcher PUT_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PutMapping");
        private static final AnnotationMatcher DELETE_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.DeleteMapping");
        private static final AnnotationMatcher PATCH_MAPPING_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.PatchMapping");

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
            // There's a weird issue with duplicated newlines on annotated classes
            J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
            doAfterVisit(new RemoveAnnotationVisitor(REQUEST_BODY_MATCHER));
            return cu.withClasses(ListUtils.mapFirst(cu.getClasses(),
                    cd -> cd
                            .withPrefix(cd.getPrefix().withWhitespace("\n\n"))
                            .withLeadingAnnotations(ListUtils.mapFirst(cd.getLeadingAnnotations(),
                                    ann -> ann.withPrefix(ann.getPrefix().withWhitespace(""))))));
        }

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
                } else if ("Path".equals(annotation.getSimpleName())) {
                    hasPath = true;
                }
            }

            // Add @Path("") only if:
            // 1. We have @RestController or (@Controller + @ResponseBody)
            // 2. We don't have @RequestMapping (which will be converted to @Path)
            // 3. We don't already have @Path
            if ((hasRestController || (hasController && hasResponseBody)) && !hasRequestMapping && !hasPath) {
                maybeAddImport("jakarta.ws.rs.Path");
                return JavaTemplate.builder("@Path(\"\")")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                        .imports("jakarta.ws.rs.Path")
                        .build()
                        .apply(getCursor(), cd.getCoordinates().addAnnotation((a1, a2) -> 0));
            }

            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            // Extract path, consumes, and produces BEFORE calling super (which will transform the annotations)
            Expression pathToAdd = null;
            Expression consumesToAdd = null;
            Expression producesToAdd = null;
            boolean hasHttpMethod = false;

            // Check what the original method has before transformation
            for (J.Annotation annotation : method.getLeadingAnnotations()) {
                if (GET_MAPPING_MATCHER.matches(annotation) ||
                        POST_MAPPING_MATCHER.matches(annotation) ||
                        PUT_MAPPING_MATCHER.matches(annotation) ||
                        DELETE_MAPPING_MATCHER.matches(annotation) ||
                        PATCH_MAPPING_MATCHER.matches(annotation)) {
                    pathToAdd = extractPathValue(annotation);
                    consumesToAdd = extractAttributeValue(annotation, "consumes");
                    producesToAdd = extractAttributeValue(annotation, "produces");
                    hasHttpMethod = true;
                } else if (REQUEST_MAPPING_MATCHER.matches(annotation)) {
                    pathToAdd = extractPathValue(annotation);
                    consumesToAdd = extractAttributeValue(annotation, "consumes");
                    producesToAdd = extractAttributeValue(annotation, "produces");
                    hasHttpMethod = true;
                }
            }

            // Now transform the annotations
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Check if @Path, @Consumes, @Produces already exist after transformation
            boolean hasPath = false;
            boolean hasConsumes = false;
            boolean hasProduces = false;
            for (J.Annotation annotation : m.getLeadingAnnotations()) {
                String simpleName = annotation.getSimpleName();
                if ("Path".equals(simpleName)) {
                    hasPath = true;
                } else if ("Consumes".equals(simpleName)) {
                    hasConsumes = true;
                } else if ("Produces".equals(simpleName)) {
                    hasProduces = true;
                }
            }

            // Add @Path if needed
            if (hasHttpMethod && pathToAdd != null && !hasPath) {
                maybeAddImport("jakarta.ws.rs.Path");
                m = JavaTemplate.builder("@Path(#{any()})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                        .imports("jakarta.ws.rs.Path")
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().addAnnotation((a1, a2) -> 0), pathToAdd);
            }

            // Add @Consumes if needed
            if (hasHttpMethod && consumesToAdd != null && !hasConsumes) {
                maybeAddImport("jakarta.ws.rs.Consumes");
                m = JavaTemplate.builder("@Consumes(#{any()})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                        .imports("jakarta.ws.rs.Consumes")
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().addAnnotation((a1, a2) -> 0), consumesToAdd);
            }

            // Add @Produces if needed
            if (hasHttpMethod && producesToAdd != null && !hasProduces) {
                maybeAddImport("jakarta.ws.rs.Produces");
                m = JavaTemplate.builder("@Produces(#{any()})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                        .imports("jakarta.ws.rs.Produces")
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().addAnnotation((a1, a2) -> 0), producesToAdd);
            }

            return m;
        }

        @Override
        public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = annotation;
            Object parent = getCursor().getParentOrThrow().getValue();

            // Handle class-level annotations
            if (parent instanceof J.ClassDeclaration) {
                if (REST_CONTROLLER_MATCHER.matches(ann)) {
                    maybeRemoveImport("org.springframework.web.bind.annotation.RestController");
                    return null; // Remove the annotation
                }
                if (CONTROLLER_MATCHER.matches(ann)) {
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
                    Expression path = extractPathValue(ann);
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
                    maybeAddImport("jakarta.ws.rs.Path");

                    // Build @Path with the correct argument
                    String pathAnnotation = path != null ? "@Path(#{any()})" : "@Path";
                    return JavaTemplate.builder(pathAnnotation)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                            .imports("jakarta.ws.rs.Path")
                            .build()
                            .apply(getCursor(), ann.getCoordinates().replace(), path);
                }
            }

            // Handle method-level annotations
            if (parent instanceof J.MethodDeclaration) {
                if (REQUEST_MAPPING_MATCHER.matches(ann)) {
                    String jaxRsAnnotation = extractMethodType(ann);
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod");
                    maybeAddImport("jakarta.ws.rs." + jaxRsAnnotation);
                    return JavaTemplate.builder("@" + jaxRsAnnotation)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                            .imports("jakarta.ws.rs." + jaxRsAnnotation)
                            .build()
                            .apply(getCursor(), ann.getCoordinates().replace());
                }
                if (GET_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "GetMapping", "GET", ctx);
                }
                if (POST_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "PostMapping", "POST", ctx);
                }
                if (PUT_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "PutMapping", "PUT", ctx);
                }
                if (DELETE_MAPPING_MATCHER.matches(ann)) {
                    return convertHttpMethodMapping(ann, "DeleteMapping", "DELETE", ctx);
                }
                if (PATCH_MAPPING_MATCHER.matches(ann)) {
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
                    return JavaTemplate.builder(newAnn)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                            .imports("jakarta.ws.rs.PathParam")
                            .build()
                            .apply(getCursor(), ann.getCoordinates().replace());
                }
                if (REQUEST_PARAM_MATCHER.matches(ann)) {
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
                    return JavaTemplate.builder(newAnn)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                            .imports("jakarta.ws.rs.QueryParam")
                            .build()
                            .apply(getCursor(), ann.getCoordinates().replace());
                }
                if (REQUEST_HEADER_MATCHER.matches(ann)) {
                    maybeRemoveImport("org.springframework.web.bind.annotation.RequestHeader");
                    maybeAddImport("jakarta.ws.rs.HeaderParam");

                    // Keep arguments if present
                    String newAnn = "@HeaderParam";
                    if (ann.getArguments() != null && !ann.getArguments().isEmpty()) {
                        // Extract the argument value
                        String args = ann.getArguments().stream()
                                .map(arg -> arg.printTrimmed(getCursor()))
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                        newAnn = "@HeaderParam(" + args + ")";
                    }
                    return JavaTemplate.builder(newAnn)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                            .imports("jakarta.ws.rs.HeaderParam")
                            .build()
                            .apply(getCursor(), ann.getCoordinates().replace());
                }
            }

            return ann;
        }

        private J.Annotation convertHttpMethodMapping(J.Annotation ann, String springMapping, String jaxRsMethod, ExecutionContext ctx) {
            maybeRemoveImport("org.springframework.web.bind.annotation." + springMapping);
            maybeAddImport("jakarta.ws.rs." + jaxRsMethod);
            return JavaTemplate.builder("@" + jaxRsMethod)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                    .imports("jakarta.ws.rs." + jaxRsMethod)
                    .build()
                    .apply(getCursor(), ann.getCoordinates().replace());
        }

        private @Nullable Expression extractPathValue(J.Annotation annotation) {
            return extractAttributeValue(annotation, "value", "path");
        }

        private @Nullable Expression extractAttributeValue(J.Annotation annotation, String... attributeNames) {
            if (annotation.getArguments() != null) {
                for (Expression arg : annotation.getArguments()) {
                    if (arg instanceof J.Literal) {
                        // For shorthand notation (single unnamed argument)
                        if (attributeNames.length > 0 && ("value".equals(attributeNames[0]) || "path".equals(attributeNames[0]))) {
                            return arg;
                        }
                    } else if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier) {
                            String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                            for (String attrName : attributeNames) {
                                if (attrName.equals(name)) {
                                    return assignment.getAssignment();
                                }
                            }
                        }
                    }
                }
            }
            return null;
        }

        private String extractMethodType(J.Annotation annotation) {
            if (annotation.getArguments() != null) {
                for (Expression arg : annotation.getArguments()) {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier &&
                                "method".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                            Expression methodExpr = assignment.getAssignment();
                            if (methodExpr instanceof J.FieldAccess) {
                                return ((J.FieldAccess) methodExpr).getSimpleName();
                            }
                            if (methodExpr instanceof J.Identifier) {
                                return ((J.Identifier) methodExpr).getSimpleName();
                            }
                        }
                    }
                }
            }
            return "GET"; // Default to GET if no method specified
        }
    }
}
