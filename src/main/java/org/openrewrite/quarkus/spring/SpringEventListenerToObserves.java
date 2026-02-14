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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
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

                        maybeRemoveImport(EVENT_LISTENER_FQN);
                        maybeAddImport(OBSERVES_FQN);

                        // Add @Observes to first parameter
                        return m.withParameters(ListUtils.mapFirst(m.getParameters(), param -> {
                            if (!(param instanceof J.VariableDeclarations)) {
                                return param;
                            }
                            J.VariableDeclarations vd = (J.VariableDeclarations) param;
                            vd = JavaTemplate.builder("@Observes")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "jakarta.enterprise.cdi-api"))
                                    .imports(OBSERVES_FQN)
                                    .build()
                                    .apply(new Cursor(getCursor(), vd), vd.getCoordinates().addAnnotation((a1, a2) -> 0));
                            return vd;
                        }));
                    }
                }
        );
    }
}
