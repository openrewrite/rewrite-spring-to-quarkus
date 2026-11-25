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
import org.openrewrite.marker.SearchResult;

@Value
@EqualsAndHashCode(callSuper = false)
public class RepositoryHasSpringBootApplication extends ScanningRecipe<ModuleIsSpringBootApplication.Accumulator> {

    String displayName = "Repository contains a Spring Boot 3 or 4 application";

    String description = "Marks every source file in a repository that has at least one module matched by " +
            "`ModuleIsSpringBootApplication`, including files outside any module such as CI workflows and Dockerfiles. " +
            "Intended as a precondition for repository-wide changes that a migrated application needs.";

    @Override
    public ModuleIsSpringBootApplication.Accumulator getInitialValue(ExecutionContext ctx) {
        return new ModuleIsSpringBootApplication.Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(ModuleIsSpringBootApplication.Accumulator acc) {
        return new ModuleIsSpringBootApplication().getScanner(acc);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ModuleIsSpringBootApplication.Accumulator acc) {
        if (acc.applicationModules().isEmpty()) {
            return TreeVisitor.noop();
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree instanceof SourceFile ? SearchResult.found(tree, "Repository has a Spring Boot application") : tree;
            }
        };
    }
}
