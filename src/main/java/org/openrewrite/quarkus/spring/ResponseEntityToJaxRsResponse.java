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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class ResponseEntityToJaxRsResponse extends Recipe {

    public static final String RESPONSE_ENTITY_FQN = "org.springframework.http.ResponseEntity";
    public static final String RESPONSE_FQN = "jakarta.ws.rs.core.Response";
    public static final String HTTP_STATUS_FQN = "org.springframework.http.HttpStatus";

    private static final MethodMatcher RESPONSE_ENTITY_OK = new MethodMatcher("org.springframework.http.ResponseEntity ok(..)");
    private static final MethodMatcher RESPONSE_ENTITY_NOT_FOUND = new MethodMatcher("org.springframework.http.ResponseEntity notFound()");
    private static final MethodMatcher RESPONSE_ENTITY_STATUS = new MethodMatcher("org.springframework.http.ResponseEntity status(..)");
    private static final MethodMatcher BODY_METHOD = new MethodMatcher("org.springframework.http.ResponseEntity$* body(..)");
    private static final MethodMatcher BUILD_METHOD = new MethodMatcher("org.springframework.http.ResponseEntity$* build()");

    @Override
    public String getDisplayName() {
        return "Convert Spring `ResponseEntity` to JAX-RS `Response`";
    }

    @Override
    public String getDescription() {
        return "Transforms Spring `ResponseEntity` patterns to JAX-RS `Response` API equivalents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(RESPONSE_ENTITY_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            private final ChangeType changeType = new ChangeType(RESPONSE_ENTITY_FQN, RESPONSE_FQN, false);

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                maybeRemoveImport(RESPONSE_ENTITY_FQN);
                maybeRemoveImport(HTTP_STATUS_FQN);
                maybeAddImport(RESPONSE_FQN);
                return (J.CompilationUnit) changeType.getVisitor().visitNonNull(c, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                if (RESPONSE_ENTITY_OK.matches(m)) {
                    if (m.getArguments().isEmpty()) {
                        return JavaTemplate.builder("Response.ok()")
                                .imports(RESPONSE_FQN)
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace());
                    }
                    return JavaTemplate.builder("Response.ok(#{any()})")
                            .imports(RESPONSE_FQN)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
                }

                if (RESPONSE_ENTITY_NOT_FOUND.matches(m)) {
                    return JavaTemplate.builder("Response.status(Response.Status.NOT_FOUND)")
                            .imports(RESPONSE_FQN)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace());
                }

                if (RESPONSE_ENTITY_STATUS.matches(m) && !m.getArguments().isEmpty()) {
                    String statusMapping = mapHttpStatusToResponseStatus(m.getArguments().get(0));
                    if (statusMapping != null) {
                        return JavaTemplate.builder("Response.status(" + statusMapping + ")")
                                .imports(RESPONSE_FQN)
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace());
                    }
                }

                if (BODY_METHOD.matches(m)) {
                    return JavaTemplate.builder("#{any()}.entity(#{any()})")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.ws.rs-api"))
                            .build()
                            .apply(getCursor(),
                                    m.getCoordinates().replace(),
                                    m.getSelect(),
                                    m.getArguments().get(0));
                }

                return m;
            }

            private @Nullable String mapHttpStatusToResponseStatus(Expression statusExpr) {
                if (statusExpr instanceof J.FieldAccess) {
                    J.FieldAccess fieldAccess = (J.FieldAccess) statusExpr;
                    String statusName = fieldAccess.getSimpleName();
                    switch (statusName) {
                        case "CREATED":
                        case "NO_CONTENT":
                        case "NOT_FOUND":
                        case "BAD_REQUEST":
                        case "UNAUTHORIZED":
                        case "FORBIDDEN":
                        case "INTERNAL_SERVER_ERROR":
                            return "Response.Status." + statusName;
                        default:
                            return null;
                    }
                }
                return null;
            }
        });
    }
}
