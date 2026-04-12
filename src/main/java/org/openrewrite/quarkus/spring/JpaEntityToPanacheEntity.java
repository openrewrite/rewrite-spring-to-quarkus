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
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class JpaEntityToPanacheEntity extends Recipe {

    private static final String ENTITY_FQN = "jakarta.persistence.Entity";
    private static final String PANACHE_ENTITY_FQN = "io.quarkus.hibernate.orm.panache.PanacheEntity";
    private static final String PANACHE_ENTITY_BASE_FQN = "io.quarkus.hibernate.orm.panache.PanacheEntityBase";
    private static final String ID_FQN = "jakarta.persistence.Id";
    private static final String GENERATED_VALUE_FQN = "jakarta.persistence.GeneratedValue";
    private static final AnnotationMatcher ENTITY_MATCHER = new AnnotationMatcher("@" + ENTITY_FQN);
    private static final AnnotationMatcher ID_MATCHER = new AnnotationMatcher("@" + ID_FQN);

    String displayName = "Convert JPA Entity to Panache Entity";

    String description = "Transforms standard JPA entities to extend Quarkus PanacheEntity, enabling the Active Record pattern with built-in CRUD operations.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(ENTITY_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        // Check if class has @Entity annotation
                        if (cd.getLeadingAnnotations().stream().noneMatch(ENTITY_MATCHER::matches)) {
                            return cd;
                        }

                        // Skip if already extends something (other than Object)
                        if (cd.getExtends() != null) {
                            String extendsType = cd.getExtends().getType() != null ?
                                    cd.getExtends().getType().toString() : "";
                            if (!extendsType.isEmpty() && !"java.lang.Object".equals(extendsType)) {
                                return cd;
                            }
                        }

                        // Find the @Id field to determine type and name
                        IdFieldInfo idFieldInfo = findIdField(cd);
                        boolean isLongId = idFieldInfo != null && idFieldInfo.isLongType;
                        boolean idFieldNamedId = idFieldInfo != null && idFieldInfo.isNamedId;

                        // Choose base class: PanacheEntity for Long id, PanacheEntityBase otherwise
                        String panacheFqn = isLongId ? PANACHE_ENTITY_FQN : PANACHE_ENTITY_BASE_FQN;
                        String panacheSimpleName = isLongId ? "PanacheEntity" : "PanacheEntityBase";

                        maybeAddImport(panacheFqn);

                        J.Identifier panacheEntityId = new J.Identifier(
                                Tree.randomId(),
                                Space.SINGLE_SPACE,
                                Markers.EMPTY,
                                emptyList(),
                                panacheSimpleName,
                                JavaType.buildType(panacheFqn),
                                null
                        );

                        cd = cd.getPadding().withExtends(new JLeftPadded<>(
                                Space.SINGLE_SPACE,
                                panacheEntityId,
                                Markers.EMPTY
                        ));

                        // Only remove @Id field and getId/setId when PanacheEntity provides the id
                        if (idFieldNamedId && isLongId) {
                            doAfterVisit(new RemoveIdFieldAndMethodsVisitor());
                        }

                        return cd;
                    }
                }
        );
    }

    private static @Nullable IdFieldInfo findIdField(J.ClassDeclaration cd) {
        for (org.openrewrite.java.tree.Statement stmt : cd.getBody().getStatements()) {
            if (stmt instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                if (vd.getLeadingAnnotations().stream().anyMatch(ID_MATCHER::matches)) {
                    boolean isNamedId = vd.getVariables().stream()
                            .anyMatch(v -> "id".equals(v.getSimpleName()));
                    JavaType type = vd.getType();
                    boolean isLongType = type != null && ("java.lang.Long".equals(type.toString()) || "long".equals(type.toString()));
                    return new IdFieldInfo(isNamedId, isLongType);
                }
            }
        }
        return null;
    }

    private static class IdFieldInfo {
        final boolean isNamedId;
        final boolean isLongType;

        IdFieldInfo(boolean isNamedId, boolean isLongType) {
            this.isNamedId = isNamedId;
            this.isLongType = isLongType;
        }
    }

    /**
     * Visitor to remove the @Id annotated field named "id" and its getter/setter since PanacheEntity provides the id.
     */
    private static class RemoveIdFieldAndMethodsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

            if (vd.getLeadingAnnotations().stream().anyMatch(ID_MATCHER::matches)) {
                boolean isIdField = vd.getVariables().stream()
                        .anyMatch(v -> "id".equals(v.getSimpleName()));

                if (isIdField) {
                    maybeRemoveImport(ID_FQN);
                    maybeRemoveImport(GENERATED_VALUE_FQN);
                    return null;
                }
            }

            return vd;
        }

        @Override
        public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Only remove getId() and setId() when the @Id field is named "id"
            String methodName = m.getSimpleName();
            if ("getId".equals(methodName) || "setId".equals(methodName)) {
                return null;
            }

            return m;
        }
    }
}
