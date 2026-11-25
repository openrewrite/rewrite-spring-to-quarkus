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
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveSpringBootParent extends Recipe {

    private static final XPathMatcher JAVA_VERSION_PROPERTY = new XPathMatcher("/project/properties/java.version");

    String displayName = "Remove Spring Boot parent POM";

    String description = "Removes the Spring Boot 3.x or 4.x starter parent POM from Maven projects, and renames " +
                         "`java.version`, which only that parent reads, to `maven.compiler.release`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document d = super.visitDocument(document, ctx);
                Optional<Xml.Tag> properties = d.getRoot().getChild("properties");
                if (d == document ||
                    !properties.flatMap(p -> p.getChild("java.version")).isPresent() ||
                    properties.flatMap(p -> p.getChild("maven.compiler.release")).isPresent()) {
                    return d;
                }
                return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                        Xml.Tag t = super.visitTag(tag, ctx);
                        if (JAVA_VERSION_PROPERTY.matches(getCursor())) {
                            return t.withName("maven.compiler.release");
                        }
                        String value = t.getValue().orElse("");
                        return value.contains("${java.version}") ? t.withValue(value.replace("${java.version}", "${maven.compiler.release}")) : t;
                    }
                }.visitNonNull(d, ctx);
            }

            @Override
            public  Xml.@Nullable Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (isSpringBootParent(tag)) {
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

                return "org.springframework.boot".equals(groupId) &&
                       "spring-boot-starter-parent".equals(artifactId) &&
                       (version.startsWith("3.") || version.startsWith("4."));
            }
        };
    }
}
