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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

public class SpringWebToJaxRs extends Recipe {
    @Override
    public String getDisplayName() {
        return "Convert Spring Web annotations to JAX-RS";
    }

    @Override
    public String getDescription() {
        return "Converts Spring Web annotations such as `@RestController`, `@RequestMapping`, `@GetMapping`, etc., to their JAX-RS equivalents like `@Path`, `@GET`, etc.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return super.getVisitor(); // TODO: Implement the visitor that performs the conversion
    }
}
