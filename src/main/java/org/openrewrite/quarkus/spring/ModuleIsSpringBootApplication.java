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
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradlePluginDescriptor;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Plugin;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

@Value
@EqualsAndHashCode(callSuper = false)
public class ModuleIsSpringBootApplication extends ScanningRecipe<ModuleIsSpringBootApplication.Accumulator> {

    private static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String SPRING_BOOT_CONFIGURATION = "org.springframework.boot.SpringBootConfiguration";

    String displayName = "Module is part of a Spring Boot 3 or 4 application";

    String description = "Marks all sources in modules that run on Spring Boot 3.x or 4.x (including snapshots and milestones), " +
            "apply the Spring Boot build plugin and have a `main` method on a class annotated with `@SpringBootApplication` or " +
            "`@SpringBootConfiguration` in the main source set, as well as the Maven parents of those modules and the modules " +
            "they depend on at runtime. Projects nested under " +
            "`src/*/resources`, such as test fixtures, are never marked; only configuration files directly in a resources " +
            "directory or its `config` directory are. Intended as a precondition, so that libraries and framework code are " +
            "not migrated as if they were applications.";

    public static class Accumulator {
        final Set<JavaProject> withMainClass = new HashSet<>();
        final Set<JavaProject> withBootPlugin = new HashSet<>();
        final Set<JavaProject> onBoot3Or4 = new HashSet<>();
        final Map<String, JavaProject> modulesByCoordinates = new HashMap<>();
        final Map<JavaProject, Set<String>> requiredCoordinates = new HashMap<>();

        @Nullable
        Set<JavaProject> applicationModules;

        Set<JavaProject> applicationModules() {
            if (applicationModules == null) {
                applicationModules = new HashSet<>(withMainClass);
                applicationModules.retainAll(withBootPlugin);
                applicationModules.retainAll(onBoot3Or4);
                Deque<JavaProject> pending = new ArrayDeque<>(applicationModules);
                while (!pending.isEmpty()) {
                    for (String coordinates : requiredCoordinates.getOrDefault(pending.pop(), emptySet())) {
                        JavaProject required = modulesByCoordinates.get(coordinates);
                        if (required != null && applicationModules.add(required)) {
                            pending.push(required);
                        }
                    }
                }
            }
            return applicationModules;
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile) || isNestedResource(((SourceFile) tree).getSourcePath())) {
                    return tree;
                }
                SourceFile sourceFile = (SourceFile) tree;
                JavaProject module = sourceFile.getMarkers().findFirst(JavaProject.class).orElse(null);
                if (module == null) {
                    return tree;
                }
                Set<String> required = acc.requiredCoordinates.computeIfAbsent(module, k -> new HashSet<>());
                sourceFile.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
                    acc.modulesByCoordinates.put(mrr.getPom().getGroupId() + ":" + mrr.getPom().getArtifactId(), module);
                    for (Plugin plugin : mrr.getPom().getPlugins()) {
                        if ("org.springframework.boot".equals(plugin.getGroupId()) && "spring-boot-maven-plugin".equals(plugin.getArtifactId())) {
                            acc.withBootPlugin.add(module);
                        }
                    }
                    for (MavenResolutionResult parent = mrr.getParent(); parent != null; parent = parent.getParent()) {
                        required.add(parent.getPom().getGroupId() + ":" + parent.getPom().getArtifactId());
                    }
                    for (ResolvedDependency dependency : mrr.getDependencies().getOrDefault(Scope.Runtime, emptyList())) {
                        required.add(dependency.getGroupId() + ":" + dependency.getArtifactId());
                        if (isSpringBoot3Or4(dependency)) {
                            acc.onBoot3Or4.add(module);
                        }
                    }
                });
                sourceFile.getMarkers().findFirst(GradleProject.class).ifPresent(gp -> {
                    acc.modulesByCoordinates.put(gp.getGroup() + ":" + gp.getName(), module);
                    for (GradlePluginDescriptor plugin : gp.getPlugins()) {
                        if ("org.springframework.boot".equals(plugin.getId())) {
                            acc.withBootPlugin.add(module);
                        }
                    }
                    GradleDependencyConfiguration runtimeClasspath = gp.getConfiguration("runtimeClasspath");
                    if (runtimeClasspath != null) {
                        for (ResolvedDependency dependency : runtimeClasspath.getResolved()) {
                            required.add(dependency.getGroupId() + ":" + dependency.getArtifactId());
                            if (isSpringBoot3Or4(dependency)) {
                                acc.onBoot3Or4.add(module);
                            }
                        }
                    }
                });
                if (sourceFile instanceof JavaSourceFile && isMainSourceSet(sourceFile) && declaresApplication((JavaSourceFile) sourceFile)) {
                    acc.withMainClass.add(module);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        Set<JavaProject> modules = acc.applicationModules();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile &&
                    !isNestedResource(((SourceFile) tree).getSourcePath()) &&
                    tree.getMarkers().findFirst(JavaProject.class).filter(modules::contains).isPresent()) {
                    return SearchResult.found(tree, "Spring Boot application module");
                }
                return tree;
            }
        };
    }

    // A version selector such as `[3.0,5.0)` would reject the snapshots and milestones that Spring's own samples build against
    private static boolean isSpringBoot3Or4(ResolvedDependency dependency) {
        return "org.springframework.boot".equals(dependency.getGroupId()) &&
               "spring-boot".equals(dependency.getArtifactId()) &&
               (dependency.getVersion().startsWith("3.") || dependency.getVersion().startsWith("4."));
    }

    private static boolean isMainSourceSet(SourceFile sourceFile) {
        return sourceFile.getMarkers().findFirst(JavaSourceSet.class)
                .map(sourceSet -> "main".equals(sourceSet.getName()))
                .orElseGet(() -> {
                    Path path = sourceFile.getSourcePath();
                    for (int i = 0; i < path.getNameCount() - 1; i++) {
                        if ("src".equals(path.getName(i).toString())) {
                            return "main".equals(path.getName(i + 1).toString());
                        }
                    }
                    return true;
                });
    }

    // The outermost resources directory decides, so the configuration of a project nested inside resources is excluded too
    private static boolean isNestedResource(Path path) {
        for (int i = 0; i + 2 < path.getNameCount() - 1; i++) {
            if ("src".equals(path.getName(i).toString()) && "resources".equals(path.getName(i + 2).toString())) {
                Path relative = path.subpath(i + 3, path.getNameCount());
                String fileName = relative.getFileName().toString();
                boolean configuration = fileName.endsWith(".properties") || fileName.endsWith(".yml") || fileName.endsWith(".yaml");
                boolean topLevel = relative.getNameCount() == 1 ||
                                   relative.getNameCount() == 2 && "config".equals(relative.getName(0).toString());
                return !(configuration && topLevel);
            }
        }
        return false;
    }

    private static boolean declaresApplication(JavaSourceFile sourceFile) {
        AtomicBoolean annotated = new AtomicBoolean();
        AtomicBoolean main = new AtomicBoolean();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Integer p) {
                for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                    if (TypeUtils.isOfClassType(annotation.getType(), SPRING_BOOT_APPLICATION) ||
                        TypeUtils.isOfClassType(annotation.getType(), SPRING_BOOT_CONFIGURATION)) {
                        annotated.set(true);
                    }
                }
                return super.visitClassDeclaration(classDecl, p);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, Integer p) {
                if ("main".equals(method.getSimpleName())) {
                    main.set(true);
                }
                return method;
            }
        }.visit(sourceFile, 0);
        return annotated.get() && main.get();
    }
}
