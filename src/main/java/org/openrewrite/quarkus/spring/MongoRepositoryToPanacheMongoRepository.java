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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class MongoRepositoryToPanacheMongoRepository extends Recipe {

    private static final String MONGO_REPO_FQN = "org.springframework.data.mongodb.repository.MongoRepository";
    private static final String PANACHE_MONGO_REPO_FQN = "io.quarkus.mongodb.panache.PanacheMongoRepository";

    @Override
    public String getDisplayName() {
        return "Convert MongoRepository to PanacheMongoRepository";
    }

    @Override
    public String getDescription() {
        return "Transforms Spring Data `MongoRepository<T, ID>` interfaces to Quarkus `PanacheMongoRepository<T>`, dropping the ID type parameter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(MONGO_REPO_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        if (cd.getImplements() == null) {
                            return cd;
                        }

                        List<TypeTree> newImplements = ListUtils.map(cd.getImplements(), impl -> {
                            if (impl instanceof J.ParameterizedType) {
                                J.ParameterizedType pt = (J.ParameterizedType) impl;
                                if (TypeUtils.isOfClassType(pt.getType(), MONGO_REPO_FQN)) {
                                    return toPanacheMongoRepository(pt);
                                }
                            } else if (impl.getType() != null && TypeUtils.isOfClassType(impl.getType(), MONGO_REPO_FQN)) {
                                J.Identifier ident = (J.Identifier) impl;
                                return ident
                                        .withSimpleName("PanacheMongoRepository")
                                        .withType(JavaType.buildType(PANACHE_MONGO_REPO_FQN));
                            }
                            return impl;
                        });

                        if (newImplements != cd.getImplements()) {
                            maybeRemoveImport(MONGO_REPO_FQN);
                            maybeAddImport(PANACHE_MONGO_REPO_FQN);
                            cd = cd.withImplements(newImplements);
                        }
                        return cd;
                    }

                    private J.ParameterizedType toPanacheMongoRepository(J.ParameterizedType pt) {
                        JavaType panacheType = JavaType.buildType(PANACHE_MONGO_REPO_FQN);
                        J.Identifier newClazz = new J.Identifier(
                                randomId(),
                                pt.getClazz().getPrefix(),
                                Markers.EMPTY,
                                emptyList(),
                                "PanacheMongoRepository",
                                panacheType,
                                null
                        );

                        List<Expression> typeParams = pt.getTypeParameters();
                        List<Expression> newTypeParams = typeParams != null && !typeParams.isEmpty()
                                ? Collections.singletonList(typeParams.get(0))
                                : typeParams;

                        // Build a new parameterized type with only the entity type parameter
                        JavaType.Parameterized newParamType = new JavaType.Parameterized(
                                null,
                                (JavaType.FullyQualified) panacheType,
                                newTypeParams != null && !newTypeParams.isEmpty()
                                        ? Collections.singletonList(newTypeParams.get(0).getType())
                                        : null
                        );

                        return pt.withClazz(newClazz).withTypeParameters(newTypeParams).withType(newParamType);
                    }
                }
        );
    }
}
