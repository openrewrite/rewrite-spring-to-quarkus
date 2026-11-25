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
import org.openrewrite.maven.AddPlugin;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.MavenTagInsertionComparator;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Parent;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.TagNameComparator;
import org.openrewrite.xml.search.FindTags;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveSpringBootParent extends ScanningRecipe<Set<Path>> {

    private static final String FILTERED_APPLICATION_CONFIG = "**/src/main/resources/**/application*.{properties,yml,yaml}";
    private static final Pattern RESOURCE_PLACEHOLDER = Pattern.compile("@[\\w.-]+@");

    String displayName = "Remove Spring Boot parent POM";

    String description = "Removes the Spring Boot 3.x or 4.x starter parent POM from Maven projects. " +
                         "Renames `java.version`, which only that parent reads, to `maven.compiler.release` in the project, its profiles and its child modules. " +
                         "Keeps the UTF-8 source encoding and the compiler `-parameters` flag the parent configured, " +
                         "and its `@..@` resource filtering when application configuration files use such placeholders.";

    @Override
    public Set<Path> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<Path> configsWithPlaceholders) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;
                    if (PathUtils.matchesGlob(sourceFile.getSourcePath(), FILTERED_APPLICATION_CONFIG) &&
                        RESOURCE_PLACEHOLDER.matcher(sourceFile.printAll()).find()) {
                        configsWithPlaceholders.add(sourceFile.getSourcePath());
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<Path> configsWithPlaceholders) {
        return new MavenIsoVisitor<ExecutionContext>() {

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document d = super.visitDocument(document, ctx);
                boolean inheritsSpringBootParent = false;
                for (MavenResolutionResult mrr = getResolutionResult(); mrr != null && !inheritsSpringBootParent; mrr = mrr.getParent()) {
                    inheritsSpringBootParent = isSpringBootParent(mrr.getPom().getRequested().getParent());
                }
                if (!inheritsSpringBootParent) {
                    return d;
                }
                boolean keepsJavaVersion = d.getRoot().getChild("properties")
                        .filter(p -> p.getChild("java.version").isPresent() && p.getChild("maven.compiler.release").isPresent())
                        .isPresent();
                d = (Xml.Document) new MavenIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                        Xml.Tag t = super.visitTag(tag, ctx);
                        if ("java.version".equals(t.getName()) && (isPropertyTag() || isProfilePropertyTag())) {
                            Xml.Tag properties = getCursor().getParentTreeCursor().getValue();
                            return properties.getChild("maven.compiler.release").isPresent() ? t : t.withName("maven.compiler.release");
                        }
                        String value = t.getValue().orElse("");
                        return !keepsJavaVersion && value.contains("${java.version}") && !"maven.compiler.release".equals(t.getName()) ?
                                t.withValue(value.replace("${java.version}", "${maven.compiler.release}")) : t;
                    }
                }.visitNonNull(d, ctx);
                if (d.getRoot().getChild("parent").isPresent() || !isSpringBootParent(getResolutionResult().getPom().getRequested().getParent())) {
                    return d;
                }
                Map<String, String> parentProperties = new LinkedHashMap<>();
                parentProperties.put("project.build.sourceEncoding", "UTF-8");
                parentProperties.put("project.reporting.outputEncoding", "UTF-8");
                if (FindTags.find(d, "//plugin[artifactId='maven-compiler-plugin']/configuration/parameters").isEmpty()) {
                    parentProperties.put("maven.compiler.parameters", "true");
                }
                for (Map.Entry<String, String> property : parentProperties.entrySet()) {
                    if (!d.getRoot().getChild("properties").isPresent()) {
                        d = (Xml.Document) new AddToTagVisitor<>(d.getRoot(), Xml.Tag.build("<properties/>"),
                                new MavenTagInsertionComparator(d.getRoot().getChildren())).visitNonNull(d, ctx);
                    }
                    Xml.Tag properties = d.getRoot().getChild("properties").orElseThrow(IllegalStateException::new);
                    if (!properties.getChild(property.getKey()).isPresent()) {
                        d = (Xml.Document) new AddToTagVisitor<>(properties,
                                Xml.Tag.build("<" + property.getKey() + ">" + property.getValue() + "</" + property.getKey() + ">"),
                                new TagNameComparator()).visitNonNull(d, ctx);
                    }
                }
                Path pomDir = d.getSourcePath().getParent();
                if (configsWithPlaceholders.stream().anyMatch(config -> pomDir == null || config.startsWith(pomDir)) &&
                    FindTags.find(d, "/project/build/resources").isEmpty() &&
                    FindTags.find(d, "//plugin[artifactId='maven-resources-plugin']").isEmpty()) {
                    d = (Xml.Document) new AddPlugin("org.apache.maven.plugins", "maven-resources-plugin", null,
                            "<configuration><delimiters><delimiter>@</delimiter></delimiters><useDefaultDelimiters>false</useDefaultDelimiters></configuration>",
                            null, null, null).getVisitor().visitNonNull(d, ctx);
                    Xml.Tag build = d.getRoot().getChild("build").orElseThrow(IllegalStateException::new);
                    d = (Xml.Document) new AddToTagVisitor<>(build, Xml.Tag.build(
                            "<resources>" +
                            "<resource>" +
                            "<directory>src/main/resources</directory>" +
                            "<filtering>true</filtering>" +
                            "<includes>" +
                            "<include>**/application*.yml</include>" +
                            "<include>**/application*.yaml</include>" +
                            "<include>**/application*.properties</include>" +
                            "</includes>" +
                            "</resource>" +
                            "<resource>" +
                            "<directory>src/main/resources</directory>" +
                            "<excludes>" +
                            "<exclude>**/application*.yml</exclude>" +
                            "<exclude>**/application*.yaml</exclude>" +
                            "<exclude>**/application*.properties</exclude>" +
                            "</excludes>" +
                            "</resource>" +
                            "</resources>"), (existing, added) -> 1).visitNonNull(d, ctx);
                }
                return d;
            }

            @Override
            public Xml.@Nullable Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (isParentTag() && isSpringBootParent(getResolutionResult().getPom().getRequested().getParent())) {
                    return null;
                }
                return super.visitTag(tag, ctx);
            }

            private boolean isSpringBootParent(@Nullable Parent parent) {
                return parent != null &&
                       "org.springframework.boot".equals(parent.getGroupId()) &&
                       "spring-boot-starter-parent".equals(parent.getArtifactId()) &&
                       (parent.getVersion().startsWith("3.") || parent.getVersion().startsWith("4."));
            }
        };
    }
}
