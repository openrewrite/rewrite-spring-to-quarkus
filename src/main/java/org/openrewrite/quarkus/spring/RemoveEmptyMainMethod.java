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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveEmptyMainMethod extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove empty main method";
    }

    @Override
    public String getDescription() {
        return "Removes public static void main(String[] args) methods that are empty after Spring Boot application cleanup.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if ("main".equals(md.getSimpleName()) &&
                        md.getMethodType() != null &&
                        md.getMethodType().getParameterTypes().size() == 1 &&
                        md.getMethodType().getParameterTypes().get(0) instanceof JavaType.Array &&
                        TypeUtils.isOfType(((JavaType.Array)md.getMethodType().getParameterTypes().get(0)).getElemType(), JavaType.buildType("java.lang.String")) &&
                        md.hasModifier(J.Modifier.Type.Public) &&
                        md.hasModifier(J.Modifier.Type.Static)) {
                    if (md.getBody() != null && md.getBody().getStatements().isEmpty()) {
                        return null;
                    }
                }
                return md;
            }
        };
    }
}
