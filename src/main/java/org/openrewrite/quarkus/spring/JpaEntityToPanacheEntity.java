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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;

@Value
@EqualsAndHashCode(callSuper = false)
public class JpaEntityToPanacheEntity extends Recipe {

    private static final String ENTITY_FQN = "jakarta.persistence.Entity";
    private static final String PANACHE_ENTITY_FQN = "io.quarkus.hibernate.orm.panache.PanacheEntity";
    private static final String ID_FQN = "jakarta.persistence.Id";
    private static final String GENERATED_VALUE_FQN = "jakarta.persistence.GeneratedValue";

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
                        boolean hasEntityAnnotation = cd.getLeadingAnnotations().stream()
                                .anyMatch(ann -> "Entity".equals(ann.getSimpleName()) ||
                                        (ann.getAnnotationType().getType() != null &&
                                                ENTITY_FQN.equals(ann.getAnnotationType().getType().toString())));

                        if (!hasEntityAnnotation) {
                            return cd;
                        }

                        // Skip if already extends something (other than Object)
                        if (cd.getExtends() != null) {
                            String extendsType = cd.getExtends().getType() != null ?
                                    cd.getExtends().getType().toString() : "";
                            if (!extendsType.isEmpty() && !extendsType.equals("java.lang.Object")) {
                                // Already has a superclass, don't modify
                                return cd;
                            }
                        }

                        // Add PanacheEntity as superclass
                        maybeAddImport(PANACHE_ENTITY_FQN);

                        // Create the extends identifier with proper spacing
                        J.Identifier panacheEntityId = new J.Identifier(
                                Tree.randomId(),
                                Space.SINGLE_SPACE,  // Space before "PanacheEntity" (after "extends")
                                Markers.EMPTY,
                                Collections.emptyList(),
                                "PanacheEntity",
                                JavaType.buildType(PANACHE_ENTITY_FQN),
                                null
                        );

                        // Use padding to set proper extends clause with space before "extends"
                        cd = cd.getPadding().withExtends(new JLeftPadded<>(
                                Space.SINGLE_SPACE,  // Space before "extends" keyword
                                panacheEntityId,
                                Markers.EMPTY
                        ));

                        // Remove @Id field and its getter/setter
                        doAfterVisit(new RemoveIdFieldAndMethodsVisitor());

                        return cd;
                    }
                }
        );
    }

    /**
     * Visitor to remove the @Id annotated field and its getter/setter since PanacheEntity provides the id.
     */
    private static class RemoveIdFieldAndMethodsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

            // Check if this field has @Id annotation
            boolean hasIdAnnotation = vd.getLeadingAnnotations().stream()
                    .anyMatch(ann -> "Id".equals(ann.getSimpleName()) ||
                            (ann.getAnnotationType().getType() != null &&
                                    ID_FQN.equals(ann.getAnnotationType().getType().toString())));

            if (hasIdAnnotation) {
                // Check if the field is named "id" - if so, remove it entirely
                boolean isIdField = vd.getVariables().stream()
                        .anyMatch(v -> "id".equals(v.getSimpleName()));

                if (isIdField) {
                    maybeRemoveImport(ID_FQN);
                    maybeRemoveImport(GENERATED_VALUE_FQN);
                    //noinspection DataFlowIssue
                    return null;
                }
            }

            return vd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Remove getId() and setId() methods
            String methodName = m.getSimpleName();
            if ("getId".equals(methodName) || "setId".equals(methodName)) {
                //noinspection DataFlowIssue
                return null;
            }

            return m;
        }
    }
}
