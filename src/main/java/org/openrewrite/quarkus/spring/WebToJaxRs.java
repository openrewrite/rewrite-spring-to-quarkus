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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class WebToJaxRs extends Recipe {
    private static final String SPRING_WEB = "org.springframework.web.bind.annotation.";
    private static final String JAX_RS = "jakarta.ws.rs.";
    private static final String RESOURCE = "IS_JAX_RS_RESOURCE";

    private static final AnnotationMatcher REST_CONTROLLER_MATCHER = new AnnotationMatcher("@" + SPRING_WEB + "RestController");
    private static final AnnotationMatcher CONTROLLER_MATCHER = new AnnotationMatcher("@org.springframework.stereotype.Controller");
    private static final AnnotationMatcher RESPONSE_BODY_MATCHER = new AnnotationMatcher("@" + SPRING_WEB + "ResponseBody");
    private static final AnnotationMatcher REQUEST_MAPPING_MATCHER = new AnnotationMatcher("@" + SPRING_WEB + "RequestMapping");
    private static final AnnotationMatcher REQUEST_BODY_MATCHER = new AnnotationMatcher("@" + SPRING_WEB + "RequestBody");
    private static final AnnotationMatcher FEIGN_CLIENT_MATCHER = new AnnotationMatcher("@org.springframework.cloud.openfeign.FeignClient");

    private static final List<String> JAX_RS_HTTP_METHODS = Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    @Getter
    final String displayName = "Convert Spring Web annotations to JAX-RS";

    @Getter
    final String description = "Converts Spring Web annotations such as `@RestController`, `@RequestMapping`, `@GetMapping`, etc., " +
            "to their JAX-RS equivalents like `@Path`, `@GET`, etc. MVC view controllers, and handler methods that have " +
            "no single JAX-RS equivalent, are left unchanged and marked for manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("org.springframework.stereotype.Controller", false),
                        new UsesType<>(SPRING_WEB + "*", false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        List<J.Annotation> annotations = classDecl.getLeadingAnnotations();
                        J.Annotation controller = find(annotations, CONTROLLER_MATCHER);
                        J.Annotation requestMapping = find(annotations, REQUEST_MAPPING_MATCHER);
                        boolean hasHandlers = false;
                        boolean onlyRestHandlers = true;
                        for (Statement statement : classDecl.getBody().getStatements()) {
                            if (statement instanceof J.MethodDeclaration && findMapping(((J.MethodDeclaration) statement).getLeadingAnnotations()) != null) {
                                J.MethodDeclaration handler = (J.MethodDeclaration) statement;
                                JavaType returnType = handler.getReturnTypeExpression() == null ? null : handler.getReturnTypeExpression().getType();
                                hasHandlers = true;
                                onlyRestHandlers &= find(handler.getAllAnnotations(), RESPONSE_BODY_MATCHER) != null ||
                                        TypeUtils.isAssignableTo("org.springframework.http.HttpEntity", returnType) ||
                                        TypeUtils.isAssignableTo("jakarta.ws.rs.core.Response", returnType);
                            }
                        }

                        boolean resource;
                        if (find(annotations, FEIGN_CLIENT_MATCHER) != null) {
                            resource = false;
                        } else if (find(annotations, REST_CONTROLLER_MATCHER) != null ||
                                   (controller != null && find(annotations, RESPONSE_BODY_MATCHER) != null)) {
                            resource = true;
                        } else if (controller != null) {
                            if (hasHandlers && !onlyRestHandlers) {
                                return classDecl.withLeadingAnnotations(ListUtils.map(annotations, a -> a == controller ?
                                        SearchResult.mergingFound(a, "Spring MVC view controller; migrate to Qute templates manually") : a));
                            }
                            resource = hasHandlers;
                        } else {
                            resource = hasHandlers && classDecl.getKind() == J.ClassDeclaration.Kind.Type.Interface;
                        }

                        if (resource && requestMapping != null && elements(attribute(requestMapping, "value", "path")).size() > 1) {
                            return classDecl.withLeadingAnnotations(ListUtils.map(annotations, a -> a == requestMapping ?
                                    SearchResult.mergingFound(a, "JAX-RS `@Path` supports a single path") : a));
                        }

                        getCursor().putMessage(RESOURCE, resource);
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (!resource) {
                            return cd;
                        }

                        J.Annotation anchor = requestMapping != null ? requestMapping :
                                controller != null ? controller : find(annotations, REST_CONTROLLER_MATCHER);
                        if (anchor == null) {
                            return cd;
                        }
                        List<Expression> paths = anchor == requestMapping ? elements(attribute(requestMapping, "value", "path")) : emptyList();
                        J.Annotation path = jaxRs("Path", paths.isEmpty() ? stringLiteral("") : paths.get(0), anchor.getPrefix());
                        J.Annotation replaced = anchor;
                        List<J.Annotation> converted = ListUtils.map(cd.getLeadingAnnotations(), a -> {
                            if (a == replaced) {
                                return path;
                            }
                            return REST_CONTROLLER_MATCHER.matches(a) || CONTROLLER_MATCHER.matches(a) ||
                                   RESPONSE_BODY_MATCHER.matches(a) || REQUEST_MAPPING_MATCHER.matches(a) ? null : a;
                        });
                        maybeRemoveImport(SPRING_WEB + "RestController");
                        maybeRemoveImport("org.springframework.stereotype.Controller");
                        maybeRemoveImport(SPRING_WEB + "ResponseBody");
                        maybeRemoveImport(SPRING_WEB + "RequestMapping");
                        return cd.withLeadingAnnotations(ListUtils.mapFirst(converted, a -> a.withPrefix(annotations.get(0).getPrefix())));
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        J.Annotation mapping = findMapping(m.getLeadingAnnotations());
                        if (mapping == null || !getCursor().getNearestMessage(RESOURCE, false)) {
                            return m;
                        }

                        String mappingName = TypeUtils.asFullyQualified(mapping.getType()).getClassName();
                        String httpMethod = mappingName.replace("Mapping", "").toUpperCase();
                        String problem = null;
                        if ("REQUEST".equals(httpMethod)) {
                            List<Expression> methods = elements(attribute(mapping, "method"));
                            if (methods.isEmpty()) {
                                problem = "Spring maps every HTTP method here; add the JAX-RS HTTP method annotation manually";
                            } else if (methods.size() > 1) {
                                problem = "JAX-RS needs a separate resource method per HTTP method";
                            } else {
                                httpMethod = methods.get(0) instanceof J.FieldAccess ? ((J.FieldAccess) methods.get(0)).getSimpleName() :
                                        methods.get(0) instanceof J.Identifier ? ((J.Identifier) methods.get(0)).getSimpleName() : "";
                                if (!JAX_RS_HTTP_METHODS.contains(httpMethod)) {
                                    problem = "JAX-RS has no `" + httpMethod + "` HTTP method annotation";
                                }
                            }
                        }
                        List<Expression> paths = elements(attribute(mapping, "value", "path"));
                        if (paths.size() > 1) {
                            problem = "JAX-RS `@Path` supports a single path";
                        }
                        for (Statement parameter : m.getParameters()) {
                            if (parameter instanceof J.VariableDeclarations) {
                                J.VariableDeclarations param = (J.VariableDeclarations) parameter;
                                J.Annotation binding = findParamBinding(param.getLeadingAnnotations());
                                JavaType.Array array = TypeUtils.asArray(param.getType());
                                JavaType.Parameterized parameterized = TypeUtils.asParameterized(param.getType());
                                JavaType elementType = array != null ? array.getElemType() :
                                        parameterized != null && parameterized.getTypeParameters().size() == 1 ? parameterized.getTypeParameters().get(0) : param.getType();
                                if (TypeUtils.isAssignableTo("org.springframework.web.multipart.MultipartFile", elementType)) {
                                    problem = "Migrate `MultipartFile` to `@RestForm FileUpload` manually";
                                } else if (binding != null && attribute(binding, "value", "name") == null &&
                                           (TypeUtils.isAssignableTo("java.util.Map", param.getType()) ||
                                            TypeUtils.isOfClassType(param.getType(), "org.springframework.http.HttpHeaders"))) {
                                    problem = "Binds all values; migrate to `@Context HttpHeaders` or `@Context UriInfo` manually";
                                }
                            }
                        }
                        if (problem != null) {
                            String description = problem;
                            return m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), a -> a == mapping ?
                                    SearchResult.mergingFound(a, description) : a));
                        }

                        Space newLine = Space.format("\n" + m.getPrefix().getIndent());
                        List<J.Annotation> jaxRsAnnotations = new ArrayList<>();
                        jaxRsAnnotations.add(jaxRs(httpMethod, null, mapping.getPrefix()));
                        if (!paths.isEmpty()) {
                            jaxRsAnnotations.add(jaxRs("Path", paths.get(0), newLine));
                        }
                        Expression consumes = attribute(mapping, "consumes");
                        if (consumes != null) {
                            jaxRsAnnotations.add(jaxRs("Consumes", consumes, newLine));
                        }
                        Expression produces = attribute(mapping, "produces");
                        if (produces != null) {
                            jaxRsAnnotations.add(jaxRs("Produces", produces, newLine));
                        }
                        m = m.withLeadingAnnotations(ListUtils.flatMap(m.getLeadingAnnotations(), a -> a == mapping ? jaxRsAnnotations : a));
                        m = m.withParameters(ListUtils.map(m.getParameters(), p -> p instanceof J.VariableDeclarations ? convertParameter((J.VariableDeclarations) p) : p));
                        m = (J.MethodDeclaration) new RemoveAnnotationVisitor(RESPONSE_BODY_MATCHER).visitNonNull(m, ctx, getCursor().getParentOrThrow());
                        m = (J.MethodDeclaration) new RemoveAnnotationVisitor(REQUEST_BODY_MATCHER).visitNonNull(m, ctx, getCursor().getParentOrThrow());
                        maybeRemoveImport(SPRING_WEB + mappingName);
                        maybeRemoveImport(SPRING_WEB + "RequestMethod");
                        maybeRemoveImport(SPRING_WEB + "ResponseBody");
                        maybeRemoveImport(SPRING_WEB + "RequestBody");
                        return m;
                    }

                    private J.VariableDeclarations convertParameter(J.VariableDeclarations param) {
                        J.Annotation binding = findParamBinding(param.getLeadingAnnotations());
                        if (binding == null) {
                            return param;
                        }
                        String springName = TypeUtils.asFullyQualified(binding.getType()).getClassName();
                        String jaxRsName = "PathVariable".equals(springName) ? "PathParam" :
                                "RequestParam".equals(springName) ? "QueryParam" :
                                "RequestHeader".equals(springName) ? "HeaderParam" : "CookieParam";
                        Expression name = attribute(binding, "value", "name");
                        Expression defaultValue = attribute(binding, "defaultValue");
                        List<J.Annotation> jaxRsAnnotations = new ArrayList<>();
                        jaxRsAnnotations.add(jaxRs(jaxRsName, name != null ? name : stringLiteral(param.getVariables().get(0).getSimpleName()), binding.getPrefix()));
                        if (defaultValue != null) {
                            jaxRsAnnotations.add(jaxRs("DefaultValue", defaultValue, Space.SINGLE_SPACE));
                        }
                        maybeRemoveImport(SPRING_WEB + springName);
                        J.VariableDeclarations p = param.withLeadingAnnotations(ListUtils.flatMap(param.getLeadingAnnotations(), a -> a == binding ? jaxRsAnnotations : a));

                        J.Primitive primitive = p.getTypeExpression() instanceof J.Primitive ? (J.Primitive) p.getTypeExpression() : null;
                        if (defaultValue == null && primitive != null && primitive.getType() != JavaType.Primitive.Boolean &&
                            J.Literal.isLiteralValue(attribute(binding, "required"), false)) {
                            String keyword = primitive.getType().getKeyword();
                            String boxedName = primitive.getType() == JavaType.Primitive.Int ? "Integer" :
                                    primitive.getType() == JavaType.Primitive.Char ? "Character" :
                                    Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1);
                            JavaType.FullyQualified boxed = JavaType.ShallowClass.build("java.lang." + boxedName);
                            p = p.withTypeExpression(new J.Identifier(randomId(), primitive.getPrefix(), Markers.EMPTY, emptyList(), boxedName, boxed, null))
                                    .withVariables(ListUtils.map(p.getVariables(), v -> v.withType(boxed).withName(v.getName().withType(boxed))));
                        }
                        return p;
                    }

                    private J.Annotation jaxRs(String simpleName, @Nullable Expression argument, Space prefix) {
                        maybeAddImport(JAX_RS + simpleName);
                        J.Annotation annotation = new J.Annotation(randomId(), prefix, Markers.EMPTY,
                                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), simpleName, JavaType.ShallowClass.build(JAX_RS + simpleName), null),
                                null);
                        return argument == null ? annotation : annotation.withArguments(singletonList(argument.withPrefix(Space.EMPTY)));
                    }
                }
        );
    }

    private static J.@Nullable Annotation find(List<J.Annotation> annotations, AnnotationMatcher matcher) {
        for (J.Annotation annotation : annotations) {
            if (matcher.matches(annotation)) {
                return annotation;
            }
        }
        return null;
    }

    private static J.@Nullable Annotation findMapping(List<J.Annotation> annotations) {
        for (J.Annotation annotation : annotations) {
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
            if (type != null && type.getFullyQualifiedName().startsWith(SPRING_WEB) && type.getClassName().endsWith("Mapping")) {
                return annotation;
            }
        }
        return null;
    }

    private static J.@Nullable Annotation findParamBinding(List<J.Annotation> annotations) {
        for (J.Annotation annotation : annotations) {
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
            if (type != null && type.getFullyQualifiedName().startsWith(SPRING_WEB) &&
                Arrays.asList("PathVariable", "RequestParam", "RequestHeader", "CookieValue").contains(type.getClassName())) {
                return annotation;
            }
        }
        return null;
    }

    private static @Nullable Expression attribute(J.Annotation annotation, String... names) {
        List<String> attributeNames = Arrays.asList(names);
        for (Expression argument : annotation.getArguments() == null ? Collections.<Expression>emptyList() : annotation.getArguments()) {
            if (argument instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) argument;
                if (assignment.getVariable() instanceof J.Identifier &&
                    attributeNames.contains(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                    return assignment.getAssignment();
                }
            } else if (!(argument instanceof J.Empty) && attributeNames.contains("value")) {
                return argument;
            }
        }
        return null;
    }

    private static List<Expression> elements(@Nullable Expression expression) {
        if (expression instanceof J.NewArray) {
            List<Expression> initializer = ((J.NewArray) expression).getInitializer();
            return initializer == null ? emptyList() : ListUtils.filter(initializer, e -> !(e instanceof J.Empty));
        }
        return expression == null ? emptyList() : singletonList(expression);
    }

    private static J.Literal stringLiteral(String value) {
        return new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, value, "\"" + value + "\"", null, JavaType.Primitive.String);
    }
}
