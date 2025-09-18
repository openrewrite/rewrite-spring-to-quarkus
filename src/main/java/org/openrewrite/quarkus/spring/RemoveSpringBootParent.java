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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.tree.Xml;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveSpringBootParent extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove Spring Boot 3.x parent POM";
    }

    @Override
    public String getDescription() {
        return "Removes the Spring Boot 3.x starter parent POM from Maven projects.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {

            public  Xml.@Nullable Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (isSpringBootParent(tag)) {
                    // Remove the parent tag entirely
                    return null;
                }
                return super.visitTag(tag, ctx);
            }

            private boolean isSpringBootParent(Xml.Tag tag) {
                if (!"parent".equals(tag.getName())) {
                    return false;
                }

                // Check if this is a Spring Boot parent by examining its children
                String groupId = tag.getChildValue("groupId").orElse("");
                String artifactId = tag.getChildValue("artifactId").orElse("");
                String version = tag.getChildValue("version").orElse("");

                // Only process Spring Boot 3.x parent POMs
                return "org.springframework.boot".equals(groupId) &&
                       "spring-boot-starter-parent".equals(artifactId) &&
                       version.startsWith("3.");
            }
        };
    }
}
