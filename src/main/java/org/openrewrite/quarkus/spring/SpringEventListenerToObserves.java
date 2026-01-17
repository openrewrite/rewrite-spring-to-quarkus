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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringEventListenerToObserves extends Recipe {

    private static final String EVENT_LISTENER_FQN = "org.springframework.context.event.EventListener";
    private static final String OBSERVES_FQN = "jakarta.enterprise.event.Observes";

    String displayName = "Convert Spring @EventListener to CDI @Observes";

    String description = "Transforms Spring's @EventListener method annotation to CDI's @Observes parameter annotation pattern.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(EVENT_LISTENER_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                        // Check if method has @EventListener annotation
                        J.Annotation eventListenerAnnotation = null;
                        for (J.Annotation ann : m.getLeadingAnnotations()) {
                            if ("EventListener".equals(ann.getSimpleName()) ||
                                    (ann.getAnnotationType().getType() != null &&
                                            EVENT_LISTENER_FQN.equals(ann.getAnnotationType().getType().toString()))) {
                                eventListenerAnnotation = ann;
                                break;
                            }
                        }

                        if (eventListenerAnnotation == null) {
                            return m;
                        }

                        // Method must have at least one parameter
                        if (m.getParameters().isEmpty() ||
                                (m.getParameters().size() == 1 && m.getParameters().get(0) instanceof J.Empty)) {
                            return m;
                        }

                        // Remove @EventListener annotation from method
                        // Use the annotation's prefix as the method's new prefix to avoid extra blank line
                        Space annotationPrefix = eventListenerAnnotation.getPrefix();
                        List<J.Annotation> newAnnotations = new ArrayList<>();
                        for (J.Annotation ann : m.getLeadingAnnotations()) {
                            if (!ann.equals(eventListenerAnnotation)) {
                                newAnnotations.add(ann);
                            }
                        }
                        m = m.withLeadingAnnotations(newAnnotations);
                        // If no annotations remain, use the annotation's prefix for the method
                        if (newAnnotations.isEmpty()) {
                            m = m.withPrefix(annotationPrefix);
                        }

                        // Add @Observes to first parameter
                        maybeRemoveImport(EVENT_LISTENER_FQN);
                        maybeAddImport(OBSERVES_FQN);

                        // Build new parameters list with @Observes on first parameter
                        List<Statement> newParams = new ArrayList<>();
                        boolean first = true;
                        for (Statement param : m.getParameters()) {
                            if (first && param instanceof J.VariableDeclarations) {
                                first = false;
                                J.VariableDeclarations vd = (J.VariableDeclarations) param;

                                // Create @Observes annotation manually
                                J.Identifier observesId = new J.Identifier(
                                        Tree.randomId(),
                                        Space.EMPTY,
                                        Markers.EMPTY,
                                        Collections.emptyList(),
                                        "Observes",
                                        JavaType.buildType(OBSERVES_FQN),
                                        null
                                );
                                J.Annotation observesAnn = new J.Annotation(
                                        Tree.randomId(),
                                        Space.EMPTY,
                                        Markers.EMPTY,
                                        observesId,
                                        null
                                );

                                // Add annotation to parameter, preserving the parameter's prefix
                                List<J.Annotation> paramAnnotations = new ArrayList<>(vd.getLeadingAnnotations());
                                paramAnnotations.add(0, observesAnn);
                                // Add space after the annotation by updating the type tree prefix
                                vd = vd.withLeadingAnnotations(paramAnnotations);
                                if (vd.getTypeExpression() != null) {
                                    vd = vd.withTypeExpression(vd.getTypeExpression().withPrefix(Space.SINGLE_SPACE));
                                }
                                newParams.add(vd);
                            } else {
                                newParams.add(param);
                            }
                        }

                        m = m.withParameters(newParams);
                        return m;
                    }
                }
        );
    }
}
