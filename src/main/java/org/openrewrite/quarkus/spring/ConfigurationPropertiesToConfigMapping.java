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
import org.openrewrite.*;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import org.openrewrite.internal.ListUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class ConfigurationPropertiesToConfigMapping extends Recipe {

    private static final String CONFIG_PROPS_FQN = "org.springframework.boot.context.properties.ConfigurationProperties";
    private static final String CONFIG_MAPPING_FQN = "io.smallrye.config.ConfigMapping";
    private static final AnnotationMatcher CONFIG_PROPS_MATCHER = new AnnotationMatcher("@" + CONFIG_PROPS_FQN);

    String displayName = "Convert @ConfigurationProperties class to @ConfigMapping interface";

    String description = "Converts Spring Boot @ConfigurationProperties classes to Quarkus @ConfigMapping interfaces. " +
            "Changes the class to an interface, converts getter methods to interface method declarations, " +
            "and removes fields, setters, and constructors.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(CONFIG_PROPS_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        if (cd.getLeadingAnnotations().stream().noneMatch(CONFIG_PROPS_MATCHER::matches)) {
                            return cd;
                        }

                        // Change annotation from @ConfigurationProperties to @ConfigMapping
                        cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), ann -> {
                            if (!CONFIG_PROPS_MATCHER.matches(ann)) {
                                return ann;
                            }
                            J.Identifier configMappingId = new J.Identifier(
                                    Tree.randomId(),
                                    Space.EMPTY,
                                    Markers.EMPTY,
                                    emptyList(),
                                    "ConfigMapping",
                                    JavaType.buildType(CONFIG_MAPPING_FQN),
                                    null
                            );
                            J.Annotation newAnn = ann.withAnnotationType(configMappingId);

                            // Remove Spring-specific attributes, keep only prefix
                            if (newAnn.getArguments() != null) {
                                List<Expression> filteredArgs = newAnn.getArguments().stream()
                                        .filter(arg -> {
                                            if (arg instanceof J.Assignment) {
                                                J.Assignment assignment = (J.Assignment) arg;
                                                if (assignment.getVariable() instanceof J.Identifier) {
                                                    String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                                                    return "prefix".equals(name);
                                                }
                                            }
                                            return true;
                                        })
                                        .collect(Collectors.toList());
                                newAnn = newAnn.withArguments(filteredArgs.isEmpty() ? null : filteredArgs);
                            }
                            return newAnn;
                        }));

                        // Convert class to interface - both AST kind and type information
                        cd = cd.withKind(J.ClassDeclaration.Kind.Type.Interface);
                        JavaType.FullyQualified classType = cd.getType();
                        if (classType instanceof JavaType.Class) {
                            cd = cd.withType(((JavaType.Class) classType).withKind(JavaType.FullyQualified.Kind.Interface));
                        }

                        // Transform body: remove fields, constructors, setters; convert getters to interface methods
                        List<Statement> newStatements = new ArrayList<>();
                        for (Statement stmt : cd.getBody().getStatements()) {
                            if (stmt instanceof J.VariableDeclarations) {
                                continue;
                            }
                            if (stmt instanceof J.MethodDeclaration) {
                                J.MethodDeclaration method = (J.MethodDeclaration) stmt;

                                if (method.isConstructor()) {
                                    continue;
                                }

                                String methodName = method.getSimpleName();

                                if (methodName.startsWith("set") && methodName.length() > 3) {
                                    continue;
                                }

                                if (methodName.startsWith("get") && methodName.length() > 3 &&
                                    method.getParameters().stream().allMatch(p -> p instanceof J.Empty)) {
                                    String propertyName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                                    newStatements.add(convertGetterToInterfaceMethod(method, propertyName));
                                    continue;
                                }
                                if (methodName.startsWith("is") && methodName.length() > 2 &&
                                    method.getParameters().stream().allMatch(p -> p instanceof J.Empty)) {
                                    String propertyName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
                                    newStatements.add(convertGetterToInterfaceMethod(method, propertyName));
                                    continue;
                                }

                                // Keep other methods but remove body for interface
                                newStatements.add(stripForInterface(method));
                            } else {
                                newStatements.add(stmt);
                            }
                        }

                        // Fix spacing: first statement gets single newline, rest get blank line
                        for (int i = 0; i < newStatements.size(); i++) {
                            Statement s = newStatements.get(i);
                            if (i == 0) {
                                newStatements.set(i, s.withPrefix(Space.format("\n    ")));
                            } else {
                                newStatements.set(i, s.withPrefix(Space.format("\n\n    ")));
                            }
                        }

                        cd = cd.withBody(cd.getBody().withStatements(newStatements));

                        maybeRemoveImport(CONFIG_PROPS_FQN);
                        doAfterVisit(new AddImport<>(CONFIG_MAPPING_FQN, null, false));

                        return cd;
                    }

                    private J.MethodDeclaration convertGetterToInterfaceMethod(J.MethodDeclaration getter, String propertyName) {
                        J.MethodDeclaration m = stripForInterface(getter)
                                .withName(getter.getName().withSimpleName(propertyName));
                        // Update method type information with the new name
                        if (m.getMethodType() != null) {
                            m = m.withMethodType(m.getMethodType().withName(propertyName));
                        }
                        return m;
                    }

                    private J.MethodDeclaration stripForInterface(J.MethodDeclaration method) {
                        J.MethodDeclaration m = method
                                .withBody(null)
                                .withModifiers(Collections.emptyList());
                        // When modifiers are removed, reset the return type's prefix
                        if (m.getReturnTypeExpression() != null) {
                            m = m.withReturnTypeExpression(m.getReturnTypeExpression().withPrefix(Space.EMPTY));
                        }
                        return m;
                    }
                }
        );
    }
}
