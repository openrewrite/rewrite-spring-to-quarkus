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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.AddDependency;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertAnnotationToDependency extends ScanningRecipe<ConvertAnnotationToDependency.Accumulator> {

    private static final Map<String, String> ANNOTATION_FQN_TO_DEPENDENCY = new HashMap<>();

    static {
        ANNOTATION_FQN_TO_DEPENDENCY.put("org.springframework.scheduling.annotation.EnableScheduling", "quarkus-scheduler");
        ANNOTATION_FQN_TO_DEPENDENCY.put("org.springframework.cache.annotation.EnableCaching", "quarkus-cache");
        ANNOTATION_FQN_TO_DEPENDENCY.put("org.springframework.data.jpa.repository.config.EnableJpaRepositories", "quarkus-spring-data-jpa");
        ANNOTATION_FQN_TO_DEPENDENCY.put("org.springframework.security.config.annotation.web.configuration.EnableWebSecurity", "quarkus-spring-security");
        ANNOTATION_FQN_TO_DEPENDENCY.put("org.springframework.boot.context.properties.EnableConfigurationProperties", "quarkus-config-yaml");
    }

    @Data
    public static class Accumulator {
        Set<String> foundAnnotationFqns = new HashSet<>();
    }

    @Override
    public String getDisplayName() {
        return "Convert Spring @EnableXXX annotations to Quarkus dependencies";
    }

    @Override
    public String getDescription() {
        return "Scans for Spring @EnableXXX annotations and conditionally adds Quarkus dependencies and removes annotations.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                if (annotation.getType() != null) {
                    String annotationFqn = annotation.getType().toString();
                    if (ANNOTATION_FQN_TO_DEPENDENCY.containsKey(annotationFqn)) {
                        acc.foundAnnotationFqns.add(annotationFqn);
                    }
                }
                return super.visitAnnotation(annotation, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.foundAnnotationFqns.isEmpty()) {
            return TreeVisitor.noop();
        }

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof Xml.Document) {
                    // Handle Maven POM files - add dependencies
                    return new MavenIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                            for (String foundAnnotationFqn : acc.foundAnnotationFqns) {
                                String dependency = ANNOTATION_FQN_TO_DEPENDENCY.get(foundAnnotationFqn);
                                doAfterVisit(new AddDependency(
                                        "io.quarkus",
                                        dependency,
                                        "x",
                                        null,
                                        null,
                                        true,
                                        null,
                                        null,
                                        null,
                                        false,
                                        null,
                                        false
                                ).getVisitor());
                            }
                            return super.visitDocument(document, ctx);
                        }
                    }.visitNonNull(tree, ctx);
                } else if (tree instanceof J.CompilationUnit) {
                    // Handle Java files - remove annotations
                    return new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                            if (annotation.getType() != null) {
                                String annotationFqn = annotation.getType().toString();
                                if (acc.foundAnnotationFqns.contains(annotationFqn)) {
                                    doAfterVisit(new RemoveAnnotation(annotationFqn).getVisitor());
                                }
                            }
                            return super.visitAnnotation(annotation, ctx);
                        }
                    }.visitNonNull(tree, ctx);
                }
                return tree;
            }
        };
    }
}
