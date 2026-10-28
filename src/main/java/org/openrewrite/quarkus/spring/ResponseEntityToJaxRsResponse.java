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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class ResponseEntityToJaxRsResponse extends Recipe {

    private static final String RESPONSE_ENTITY = "org.springframework.http.ResponseEntity";
    private static final String HTTP_ENTITY = "org.springframework.http.HttpEntity";
    private static final String HTTP_STATUS = "org.springframework.http.HttpStatus";
    private static final String SPRING_MEDIA_TYPE = "org.springframework.http.MediaType";
    private static final String RESPONSE = "jakarta.ws.rs.core.Response";
    private static final String BUILDER = "#{any(jakarta.ws.rs.core.Response.ResponseBuilder)}";
    private static final List<String> ASYNC_WRAPPERS = Arrays.asList(
            "reactor.core.publisher.Mono", "java.util.concurrent.CompletionStage", "java.util.concurrent.CompletableFuture");

    private static final String RESOURCE = "IS_JAX_RS_RESOURCE";
    private static final String RESPONSE_NAME_TAKEN = "RESPONSE_NAME_TAKEN";
    private static final String CONVERT = "CONVERT_TO_RESPONSE";
    private static final String MANUALLY = "; convert this method to `jakarta.ws.rs.core.Response` manually";

    private static final Map<String, String> STATUSES = new HashMap<>();
    private static final Map<String, String> MEDIA_TYPES = new HashMap<>();

    static {
        for (String entry : ("CONTINUE=100 SWITCHING_PROTOCOLS=101 PROCESSING=102 EARLY_HINTS=103 CHECKPOINT=103 " +
                             "OK CREATED ACCEPTED NON_AUTHORITATIVE_INFORMATION=203 NO_CONTENT RESET_CONTENT PARTIAL_CONTENT " +
                             "MULTI_STATUS=207 ALREADY_REPORTED=208 IM_USED=226 MULTIPLE_CHOICES MOVED_PERMANENTLY FOUND " +
                             "MOVED_TEMPORARILY=FOUND SEE_OTHER NOT_MODIFIED USE_PROXY TEMPORARY_REDIRECT PERMANENT_REDIRECT " +
                             "BAD_REQUEST UNAUTHORIZED PAYMENT_REQUIRED FORBIDDEN NOT_FOUND METHOD_NOT_ALLOWED NOT_ACCEPTABLE " +
                             "PROXY_AUTHENTICATION_REQUIRED REQUEST_TIMEOUT CONFLICT GONE LENGTH_REQUIRED PRECONDITION_FAILED " +
                             "CONTENT_TOO_LARGE=REQUEST_ENTITY_TOO_LARGE PAYLOAD_TOO_LARGE=REQUEST_ENTITY_TOO_LARGE " +
                             "REQUEST_ENTITY_TOO_LARGE URI_TOO_LONG=REQUEST_URI_TOO_LONG REQUEST_URI_TOO_LONG " +
                             "UNSUPPORTED_MEDIA_TYPE REQUESTED_RANGE_NOT_SATISFIABLE EXPECTATION_FAILED I_AM_A_TEAPOT=418 " +
                             "INSUFFICIENT_SPACE_ON_RESOURCE=419 METHOD_FAILURE=420 DESTINATION_LOCKED=421 MISDIRECTED_REQUEST=421 " +
                             "UNPROCESSABLE_CONTENT=422 UNPROCESSABLE_ENTITY=422 LOCKED=423 FAILED_DEPENDENCY=424 TOO_EARLY=425 " +
                             "UPGRADE_REQUIRED=426 PRECONDITION_REQUIRED TOO_MANY_REQUESTS REQUEST_HEADER_FIELDS_TOO_LARGE " +
                             "UNAVAILABLE_FOR_LEGAL_REASONS INTERNAL_SERVER_ERROR NOT_IMPLEMENTED BAD_GATEWAY SERVICE_UNAVAILABLE " +
                             "GATEWAY_TIMEOUT HTTP_VERSION_NOT_SUPPORTED VARIANT_ALSO_NEGOTIATES=506 INSUFFICIENT_STORAGE=507 " +
                             "LOOP_DETECTED=508 BANDWIDTH_LIMIT_EXCEEDED=509 NOT_EXTENDED=510 NETWORK_AUTHENTICATION_REQUIRED").split(" ")) {
            String[] nameAndTarget = entry.split("=");
            String target = nameAndTarget[nameAndTarget.length - 1];
            STATUSES.put(nameAndTarget[0], Character.isDigit(target.charAt(0)) ? target : "Response.Status." + target);
        }
        for (String entry : ("ALL=WILDCARD APPLICATION_ATOM_XML APPLICATION_FORM_URLENCODED APPLICATION_JSON APPLICATION_OCTET_STREAM " +
                             "APPLICATION_XHTML_XML APPLICATION_XML MULTIPART_FORM_DATA TEXT_EVENT_STREAM=SERVER_SENT_EVENTS " +
                             "TEXT_HTML TEXT_PLAIN TEXT_XML").split(" ")) {
            String[] nameAndTarget = entry.split("=");
            MEDIA_TYPES.put(nameAndTarget[0], nameAndTarget[nameAndTarget.length - 1]);
        }
    }

    String displayName = "Convert Spring `ResponseEntity` to JAX-RS `Response`";

    String description = "Converts the `ResponseEntity` return values of JAX-RS resource methods to JAX-RS `Response`, " +
            "building each `ResponseEntity` expression with the equivalent `Response` builder chain. " +
            "Resource methods that use `ResponseEntity` in a way that has no JAX-RS equivalent are left unchanged and marked " +
            "for manual migration, and `ResponseEntity` uses outside resource methods, such as `RestTemplate` results, are left alone.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(RESPONSE_ENTITY, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                getCursor().putMessage(RESPONSE_NAME_TAKEN, cu.getImports().stream()
                        .anyMatch(i -> "Response".equals(i.getClassName()) && !RESPONSE.equals(i.getTypeName())) ||
                        cu.getClasses().stream().anyMatch(c -> "Response".equals(c.getSimpleName())));
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                getCursor().putMessage(RESOURCE, WebToJaxRs.isResource(classDecl) && WebToJaxRs.hasSingleClassPath(classDecl));
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                TypeTree returnType = method.getReturnTypeExpression();
                J.Annotation mapping = WebToJaxRs.findMapping(method.getLeadingAnnotations());
                if (returnType == null || !getCursor().getNearestMessage(RESOURCE, false) || mapping == null ||
                    WebToJaxRs.handlerProblem(method, mapping) != null) {
                    return method;
                }
                J.ParameterizedType parameterized = returnType instanceof J.ParameterizedType ? (J.ParameterizedType) returnType : null;
                J.ParameterizedType wrapper = parameterized != null && parameterized.getTypeParameters() != null &&
                                              parameterized.getTypeParameters().size() == 1 &&
                                              TypeUtils.isOfClassType(parameterized.getTypeParameters().get(0).getType(), RESPONSE_ENTITY) &&
                                              ASYNC_WRAPPERS.stream().anyMatch(w -> TypeUtils.isOfClassType(parameterized.getType(), w)) ? parameterized : null;
                boolean httpEntity = TypeUtils.isOfClassType(returnType.getType(), HTTP_ENTITY);
                if (wrapper == null && !httpEntity && !TypeUtils.isOfClassType(returnType.getType(), RESPONSE_ENTITY)) {
                    return mentionsResponseEntity(returnType.getType()) ?
                            method.withReturnTypeExpression(SearchResult.mergingFound(returnType, "Unsupported return type" + MANUALLY)) : method;
                }

                String problem = getCursor().firstEnclosing(J.CompilationUnit.class) == null ? "Only Java sources are converted" :
                        method.getBody() == null || TypeUtils.findOverriddenMethod(method.getMethodType()).isPresent() ?
                                "This method and its overrides must change together" :
                                getCursor().getNearestMessage(RESPONSE_NAME_TAKEN, false) ? "Another `Response` type is in scope" : null;
                if (problem != null) {
                    return method.withReturnTypeExpression(SearchResult.mergingFound(returnType, problem + MANUALLY));
                }
                Validator validator = new Validator(httpEntity);
                J.Block validated = (J.Block) validator.visitNonNull(method.getBody(), ctx, getCursor());
                if (validator.unconvertible) {
                    return method.withBody(validated);
                }

                getCursor().putMessage(CONVERT, true);
                J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                JavaType.FullyQualified responseType = JavaType.ShallowClass.build(RESPONSE);
                J.Identifier response = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "Response", responseType, null);
                JavaType.Parameterized wrapperType = wrapper == null ? null : TypeUtils.asParameterized(wrapper.getType());
                TypeTree converted = wrapper == null ? response.withPrefix(returnType.getPrefix()) :
                        wrapper.withTypeParameters(singletonList(response.withPrefix(wrapper.getTypeParameters().get(0).getPrefix())))
                                .withType(wrapperType == null ? wrapper.getType() : wrapperType.withTypeParameters(singletonList(responseType)));
                JavaType.Method methodType = m.getMethodType() == null ? null : m.getMethodType().withReturnType(converted.getType());
                maybeRemoveImport(RESPONSE_ENTITY);
                maybeRemoveImport(HTTP_ENTITY);
                maybeRemoveImport(HTTP_STATUS);
                maybeAddImport(RESPONSE);
                return m.withReturnTypeExpression(converted)
                        .withMethodType(methodType)
                        .withName(m.getName().withType(methodType));
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (getCursor().getNearestMessage(CONVERT) == null || type == null || !isSpringResponse(type.getDeclaringType())) {
                    return m;
                }
                String code = jaxRsTemplate(m);
                List<Object> parameters = new ArrayList<>();
                if (code.startsWith(BUILDER)) {
                    parameters.add(m.getSelect());
                }
                List<Expression> arguments = ListUtils.filter(m.getArguments(), a -> !(a instanceof J.Empty));
                parameters.addAll(arguments.subList(0, StringUtils.countOccurrences(code, "#{") - parameters.size()));
                // Templates auto-format what they substitute, so put back the original select and arguments
                J.MethodInvocation converted = (J.MethodInvocation) template(code, m, ctx, parameters.toArray());
                if (converted.getSelect() != null && m.getSelect() != null) {
                    converted = code.startsWith(BUILDER) ? converted.getPadding().withSelect(m.getPadding().getSelect()) :
                            converted.getPadding().withSelect(converted.getPadding().getSelect().withAfter(m.getPadding().getSelect().getAfter()));
                }
                J.MethodInvocation formatted = converted.withName(converted.getName().withPrefix(m.getName().getPrefix()));
                if (formatted.getArguments().size() == m.getArguments().size()) {
                    formatted = formatted.withArguments(ListUtils.map(formatted.getArguments(), (i, a) -> a.getId().equals(m.getArguments().get(i).getId()) ?
                            m.getArguments().get(i) : a.withPrefix(m.getArguments().get(i).getPrefix())));
                }
                if (code.contains("jakarta.ws.rs.core.MediaType")) {
                    maybeRemoveImport(SPRING_MEDIA_TYPE);
                    doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(formatted));
                }
                if (code.contains("Date")) {
                    maybeAddImport("java.util.Date");
                }
                if (TypeUtils.isOfClassType(m.getType(), RESPONSE_ENTITY) && !"build".equals(m.getSimpleName())) {
                    return ((J.MethodInvocation) template(BUILDER + ".build()", formatted, ctx, formatted)).withSelect(formatted.withPrefix(Space.EMPTY));
                }
                return formatted;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                if (getCursor().getNearestMessage(CONVERT) == null || !TypeUtils.isOfClassType(n.getType(), RESPONSE_ENTITY)) {
                    return n;
                }
                List<Expression> arguments = ListUtils.filter(n.getArguments(), a -> !(a instanceof J.Empty));
                Expression status = arguments.get(arguments.size() - 1);
                String code = status(status);
                List<Object> parameters = new ArrayList<>();
                if (code.startsWith("#{")) {
                    parameters.add(status);
                }
                if (arguments.size() == 1) {
                    return template("Response.status(" + code + ").build()", n, ctx, parameters.toArray());
                }
                parameters.add(arguments.get(0));
                return template("Response.Status.OK".equals(code) ? "Response.ok(#{any()}).build()" :
                        "Response.status(" + code + ").entity(#{any()}).build()", n, ctx, parameters.toArray());
            }

            @Override
            public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference ref = (J.MemberReference) super.visitMemberReference(memberRef, ctx);
                if (getCursor().getNearestMessage(CONVERT) == null || !TypeUtils.isOfClassType(ref.getContaining().getType(), RESPONSE_ENTITY)) {
                    return ref;
                }
                String body = VariableNameUtils.generateVariableName("body", getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                return JavaTemplate.builder(body + " -> Response.ok(" + body + ").build()")
                        .contextSensitive()
                        .imports(RESPONSE)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                        .build()
                        .apply(getCursor(), ref.getCoordinates().replace());
            }

            private J template(String code, Expression tree, ExecutionContext ctx, Object... parameters) {
                return JavaTemplate.builder(code)
                        .imports(RESPONSE, "java.util.Date")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                        .build()
                        .apply(new Cursor(getCursor().getParentOrThrow(), tree), tree.getCoordinates().replace(), parameters);
            }
        });
    }

    private static class Validator extends JavaIsoVisitor<ExecutionContext> {
        private final boolean httpEntity;
        private boolean unconvertible;

        Validator(boolean httpEntity) {
            this.httpEntity = httpEntity;
        }

        @Override
        public @Nullable J preVisit(J tree, ExecutionContext ctx) {
            Object parent = getCursor().getParentTreeCursor().getValue();
            String problem = null;
            if (tree instanceof J.Return && httpEntity && ((J.Return) tree).getExpression() != null &&
                getCursor().dropParentUntil(t -> t instanceof J.Lambda || t instanceof J.MethodDeclaration).getValue() instanceof J.MethodDeclaration &&
                !TypeUtils.isOfClassType(((J.Return) tree).getExpression().getType(), RESPONSE_ENTITY)) {
                stopAfterPreVisit();
                unconvertible = true;
                J.Return r = (J.Return) tree;
                return r.withExpression(SearchResult.mergingFound(r.getExpression(), "This `HttpEntity` is not a `ResponseEntity`" + MANUALLY));
            }
            if (tree instanceof J.VariableDeclarations && mentionsResponseEntity(((J.VariableDeclarations) tree).getType())) {
                stopAfterPreVisit();
                unconvertible = true;
                J.VariableDeclarations v = (J.VariableDeclarations) tree;
                String description = "This `ResponseEntity` variable cannot be converted" + MANUALLY;
                return v.getTypeExpression() == null ? SearchResult.mergingFound(v, description) :
                        v.withTypeExpression(SearchResult.mergingFound(v.getTypeExpression(), description));
            }
            if (!(tree instanceof Expression) || parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getName() == tree) {
                return tree;
            }
            Expression e = (Expression) tree;
            boolean foreign = false;
            if (e instanceof J.MethodInvocation) {
                J.MethodInvocation m = (J.MethodInvocation) e;
                JavaType.Method type = m.getMethodType();
                if (type != null && isSpringResponse(type.getDeclaringType())) {
                    if (jaxRsTemplate(m) == null) {
                        problem = "`" + m.getSimpleName() + "(..)` has no JAX-RS equivalent";
                    }
                } else if (!isPassThrough(type)) {
                    foreign = true;
                    if (mentionsResponseEntity(m.getType()) && flowsIntoResult(parent)) {
                        problem = "This `ResponseEntity` is not built here";
                    } else if (m.getSelect() != null && mentionsResponseEntity(m.getSelect().getType())) {
                        problem = "`" + m.getSimpleName() + "(..)` has no JAX-RS equivalent";
                    }
                }
            } else if (e instanceof J.NewClass) {
                J.NewClass n = (J.NewClass) e;
                List<Expression> arguments = ListUtils.filter(n.getArguments(), a -> !(a instanceof J.Empty));
                if (TypeUtils.isOfClassType(n.getType(), RESPONSE_ENTITY) ?
                        n.getBody() != null || arguments.isEmpty() || arguments.size() > 2 || status(arguments.get(arguments.size() - 1)) == null ||
                        TypeUtils.isAssignableTo("org.springframework.util.MultiValueMap", arguments.get(0).getType()) :
                        mentionsResponseEntity(n.getType())) {
                    problem = "This `ResponseEntity` constructor has no JAX-RS equivalent";
                }
            } else if (e instanceof J.MemberReference) {
                J.MemberReference ref = (J.MemberReference) e;
                if (TypeUtils.isOfClassType(ref.getContaining().getType(), RESPONSE_ENTITY)) {
                    stopAfterPreVisit();
                    if (!"ok".equals(ref.getReference().getSimpleName()) || !(parent instanceof J.MethodInvocation) ||
                        !isPassThrough(((J.MethodInvocation) parent).getMethodType())) {
                        problem = "`ResponseEntity::" + ref.getReference().getSimpleName() + "` has no JAX-RS equivalent";
                    }
                }
            } else if (e instanceof J.Identifier && ((J.Identifier) e).getFieldType() != null && mentionsResponseEntity(e.getType())) {
                problem = "This `ResponseEntity` variable cannot be converted";
            }
            if (problem == null && !foreign && isSpringResponse(e.getType()) && !allowedPosition(e, parent)) {
                problem = "This `ResponseEntity` is used where a JAX-RS `Response` does not fit";
            }
            if (problem == null) {
                return tree;
            }
            stopAfterPreVisit();
            unconvertible = true;
            return e instanceof J.MethodInvocation ?
                    ((J.MethodInvocation) e).withName(SearchResult.mergingFound(((J.MethodInvocation) e).getName(), problem + MANUALLY)) :
                    SearchResult.mergingFound(e, problem + MANUALLY);
        }

        private boolean allowedPosition(Expression e, Object parent) {
            if (parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getSelect() == e &&
                ((J.MethodInvocation) parent).getMethodType() != null &&
                isSpringResponse(((J.MethodInvocation) parent).getMethodType().getDeclaringType())) {
                return true;
            }
            if (parent instanceof J.NewClass) {
                return ((J.NewClass) parent).getClazz() == e;
            }
            if (parent instanceof J.ParameterizedType) {
                return ((J.ParameterizedType) parent).getClazz() == e &&
                       getCursor().getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.NewClass;
            }
            return TypeUtils.isOfClassType(e.getType(), RESPONSE_ENTITY) && flowsIntoResult(parent);
        }

        private boolean flowsIntoResult(Object parent) {
            return parent instanceof J.MethodInvocation ? isPassThrough(((J.MethodInvocation) parent).getMethodType()) :
                    parent instanceof J.Return || parent instanceof J.Lambda || parent instanceof J.Parentheses || parent instanceof J.Ternary;
        }
    }

    private static @Nullable String jaxRsTemplate(J.MethodInvocation m) {
        List<Expression> arguments = ListUtils.filter(m.getArguments(), a -> !(a instanceof J.Empty));
        Expression argument = arguments.isEmpty() ? null : arguments.get(0);
        switch (m.getSimpleName()) {
            case "ok":
                return argument == null ? "Response.ok()" : "Response.ok(#{any()})";
            case "created":
                return "Response.created(#{any(java.net.URI)})";
            case "accepted":
                return "Response.accepted()";
            case "noContent":
                return "Response.noContent()";
            case "badRequest":
                return "Response.status(Response.Status.BAD_REQUEST)";
            case "notFound":
                return "Response.status(Response.Status.NOT_FOUND)";
            case "unprocessableEntity":
                return "Response.status(422)";
            case "internalServerError":
                return "Response.serverError()";
            case "status":
                String status = argument == null ? null : status(argument);
                return status == null ? null : "Response.status(" + status + ")";
            case "header":
                return arguments.size() == 2 && TypeUtils.asArray(arguments.get(1).getType()) == null ?
                        BUILDER + ".header(#{any(java.lang.String)}, #{any()})" : null;
            case "eTag":
                return BUILDER + ".tag(#{any(java.lang.String)})";
            case "location":
                return BUILDER + ".location(#{any(java.net.URI)})";
            case "lastModified":
                JavaType type = argument == null ? null : argument.getType();
                return TypeUtils.isOfClassType(type, "java.time.Instant") ? BUILDER + ".lastModified(Date.from(#{any(java.time.Instant)}))" :
                        TypeUtils.isOfClassType(type, "java.time.ZonedDateTime") ? BUILDER + ".lastModified(Date.from(#{any(java.time.ZonedDateTime)}.toInstant()))" :
                        type == JavaType.Primitive.Long ? BUILDER + ".lastModified(new Date(#{any(long)}))" : null;
            case "contentType":
                String mediaType = argument instanceof J.FieldAccess && TypeUtils.isOfClassType(argument.getType(), SPRING_MEDIA_TYPE) ?
                        MEDIA_TYPES.get(((J.FieldAccess) argument).getSimpleName()) : null;
                return mediaType == null ? null : BUILDER + ".type(jakarta.ws.rs.core.MediaType." + mediaType + ")";
            case "contentLength":
                return BUILDER + ".header(\"Content-Length\", #{any(long)})";
            case "body":
                return BUILDER + ".entity(#{any()})";
            case "build":
                return BUILDER + ".build()";
            default:
                return null;
        }
    }

    private static @Nullable String status(Expression status) {
        if (status instanceof J.FieldAccess && TypeUtils.isOfClassType(status.getType(), HTTP_STATUS)) {
            return STATUSES.get(((J.FieldAccess) status).getSimpleName());
        }
        return status.getType() == JavaType.Primitive.Int ? "#{any(int)}" : null;
    }

    private static boolean isSpringResponse(@Nullable JavaType type) {
        return TypeUtils.isOfClassType(type, RESPONSE_ENTITY) ||
               TypeUtils.isOfClassType(type, RESPONSE_ENTITY + "$BodyBuilder") ||
               TypeUtils.isOfClassType(type, RESPONSE_ENTITY + "$HeadersBuilder");
    }

    private static boolean mentionsResponseEntity(@Nullable JavaType type) {
        JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
        return isSpringResponse(type) || parameterized != null &&
                                         parameterized.getTypeParameters().stream().anyMatch(ResponseEntityToJaxRsResponse::mentionsResponseEntity);
    }

    private static boolean isPassThrough(JavaType.@Nullable Method method) {
        String declaringType = method == null ? "" : method.getDeclaringType().getFullyQualifiedName();
        return declaringType.startsWith("java.") || declaringType.startsWith("reactor.");
    }
}
