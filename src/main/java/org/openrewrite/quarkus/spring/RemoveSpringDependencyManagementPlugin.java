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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.gradle.plugins.RemovePluginVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveSpringDependencyManagementPlugin extends Recipe {

    String displayName = "Remove the Spring dependency management Gradle plugin";

    String description = "Removes the `io.spring.dependency-management` plugin from Gradle builds that don't configure " +
                         "a `dependencyManagement` block. Without the Spring Boot plugin it no longer imports the Spring Boot " +
                         "BOM, so it has nothing left to manage.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsBuildGradle<>(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                AtomicBoolean configured = new AtomicBoolean();
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                        if ("dependencyManagement".equals(method.getSimpleName())) {
                            found.set(true);
                            return method;
                        }
                        return super.visitMethodInvocation(method, found);
                    }
                }.visit(tree, configured);
                return configured.get() ? tree : new RemovePluginVisitor("io.spring.dependency-management").visit(tree, ctx);
            }
        });
    }
}
