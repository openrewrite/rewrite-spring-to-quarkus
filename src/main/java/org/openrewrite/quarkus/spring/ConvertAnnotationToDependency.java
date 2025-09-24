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
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
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


@EqualsAndHashCode(callSuper = false)
@Value
public class ConvertAnnotationToDependency extends ScanningRecipe<ConvertAnnotationToDependency.Accumulator> {

    private static final Map<AnnotationMatcher, String> ANNOTATION_MATCHER_TO_DEPENDENCY = new HashMap<>();

    static {
        ANNOTATION_MATCHER_TO_DEPENDENCY.put(new AnnotationMatcher("@org.springframework.scheduling.annotation.EnableScheduling"), "quarkus-scheduler");
        ANNOTATION_MATCHER_TO_DEPENDENCY.put(new AnnotationMatcher("@org.springframework.cache.annotation.EnableCaching"), "quarkus-cache");
        ANNOTATION_MATCHER_TO_DEPENDENCY.put(new AnnotationMatcher("@org.springframework.data.jpa.repository.config.EnableJpaRepositories"), "quarkus-spring-data-jpa");
        ANNOTATION_MATCHER_TO_DEPENDENCY.put(new AnnotationMatcher("@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity"), "quarkus-spring-security");
        ANNOTATION_MATCHER_TO_DEPENDENCY.put(new AnnotationMatcher("@org.springframework.boot.context.properties.EnableConfigurationProperties"), "quarkus-config-yaml");
    }

    @Data
    public static class Accumulator {
        Map<String, String> foundAnnotationFqnToDependency = new HashMap<>();
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
                    for (Map.Entry<AnnotationMatcher, String> entry : ANNOTATION_MATCHER_TO_DEPENDENCY.entrySet()) {
                        if (entry.getKey().matches(annotation)) {
                            acc.foundAnnotationFqnToDependency.put(annotationFqn, entry.getValue());
                            break;
                        }
                    }
                }
                return annotation;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return Preconditions.check(!acc.foundAnnotationFqnToDependency.isEmpty(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof Xml.Document) {
                    return new MavenIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                            for (String dependency : acc.foundAnnotationFqnToDependency.values()) {
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
                    return new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                            for (String annotationFqn : acc.foundAnnotationFqnToDependency.keySet()) {
                                doAfterVisit(new RemoveAnnotation(annotationFqn).getVisitor());
                            }
                            return cu;
                        }
                    }.visitNonNull(tree, ctx);
                }
                return tree;
            }
        });
    }
}
