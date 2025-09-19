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
import org.openrewrite.maven.AddPlugin;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.ManagedDependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddQuarkusMavenPlugin extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add Quarkus Maven plugin";
    }

    @Override
    public String getDescription() {
        return "Adds the Quarkus Maven plugin using the same version as the quarkus-bom in dependency management.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                MavenResolutionResult mrr = getResolutionResult();
                Optional<String> quarkusVersion = mrr.getPom().getRequested().getDependencyManagement().stream()
                        .filter(dep -> "io.quarkus.platform".equals(dep.getGroupId()) &&
                                "quarkus-bom".equals(dep.getArtifactId()))
                        .map(ManagedDependency::getVersion)
                        .findFirst();

                return quarkusVersion
                        .map(version -> addQuarkusPluginWithVersion(document, ctx, version))
                        .orElse(document);
            }

            private Xml.Document addQuarkusPluginWithVersion(Xml.Document document, ExecutionContext ctx, String version) {
                return (Xml.Document) new AddPlugin("io.quarkus.platform", "quarkus-maven-plugin", version, null, null, null, null).getVisitor().visitNonNull(document, ctx);
            }
        };
    }
}
