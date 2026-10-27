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
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class SpringBeanToCdiProduces extends Recipe {

    public static final String BEAN_FQN = "org.springframework.context.annotation.Bean";
    public static final String SCOPE_FQN = "org.springframework.context.annotation.Scope";
    private static final AnnotationMatcher BEAN_MATCHER = new AnnotationMatcher("@" + BEAN_FQN);
    private static final AnnotationMatcher SCOPE_MATCHER = new AnnotationMatcher("@" + SCOPE_FQN);

    public static final String PRODUCES_FQN = "jakarta.enterprise.inject.Produces";
    public static final String APPLICATION_SCOPED_FQN = "jakarta.enterprise.context.ApplicationScoped";
    public static final String CONFIGURABLE_BEAN_FACTORY_FQN = "org.springframework.beans.factory.config.ConfigurableBeanFactory";
    public static final String DEPENDENT_FQN = "jakarta.enterprise.context.Dependent";
    public static final String NAMED_FQN = "jakarta.inject.Named";

    private static final Map<String, String> CDI_SCOPES = new HashMap<>();

    static {
        CDI_SCOPES.put("", APPLICATION_SCOPED_FQN);
        CDI_SCOPES.put("singleton", APPLICATION_SCOPED_FQN);
        CDI_SCOPES.put("application", APPLICATION_SCOPED_FQN);
        CDI_SCOPES.put("prototype", DEPENDENT_FQN);
        CDI_SCOPES.put("request", "jakarta.enterprise.context.RequestScoped");
        CDI_SCOPES.put("session", "jakarta.enterprise.context.SessionScoped");
    }

    static final String SPRING_SECURITY = "Spring Security configuration has no CDI equivalent; migrate it to Quarkus security manually";
    static final String CONDITIONAL_CONFIGURATION = "CDI has no equivalent of Spring conditional configuration; migrate it manually, e.g. to `@IfBuildProperty`";

    String displayName = "Replace Spring `@Bean` with CDI `@Produces`";

    String description = "Transform Spring `@Bean` methods to CDI `@Produces` methods with appropriate scope annotations, " +
            "keeping any other annotations on the method. Spring Security beans, beans of conditional configuration " +
            "classes and beans in scopes CDI does not have are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(BEAN_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        List<J.Annotation> annotations = m.getLeadingAnnotations();
                        J.Annotation bean = find(annotations, BEAN_MATCHER);
                        if (bean == null) {
                            return m;
                        }
                        if (isSpringSecurityBean(m)) {
                            return m.withLeadingAnnotations(ListUtils.map(annotations, a -> a == bean ? SearchResult.found(a, SPRING_SECURITY) : a));
                        }
                        J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (enclosing != null && unconvertibleConfigurationReason(enclosing) != null) {
                            return m;
                        }

                        J.Annotation scope = find(annotations, SCOPE_MATCHER);
                        Expression scopeName = scope == null ? null : attribute(scope, "value", "scopeName");
                        String springScope = scopeName instanceof J.Literal ? String.valueOf(((J.Literal) scopeName).getValue()) :
                                scopeName instanceof J.FieldAccess ? ((J.FieldAccess) scopeName).getSimpleName().replaceFirst("^SCOPE_", "").toLowerCase() :
                                scopeName instanceof J.Identifier ? ((J.Identifier) scopeName).getSimpleName().replaceFirst("^SCOPE_", "").toLowerCase() :
                                "";
                        String cdiScope = CDI_SCOPES.get(springScope);
                        if (cdiScope == null) {
                            return m.withLeadingAnnotations(ListUtils.map(annotations, a -> a == scope ?
                                    SearchResult.found(a, "CDI has no equivalent of the Spring `" + springScope + "` scope") : a));
                        }

                        int beanIndex = annotations.indexOf(bean);
                        J declaration = m.getModifiers().isEmpty() ? m.getReturnTypeExpression() : m.getModifiers().get(0);
                        Space separator = beanIndex > 0 ? bean.getPrefix() :
                                annotations.size() > 1 ? annotations.get(1).getPrefix() :
                                        declaration != null && !declaration.getPrefix().getWhitespace().contains("\n") ? Space.SINGLE_SPACE :
                                                Space.format("\n" + m.getPrefix().getIndent());
                        List<J.Annotation> cdiAnnotations = new ArrayList<>();
                        J.Annotation produces = annotation(PRODUCES_FQN, null, bean.getPrefix());
                        List<String> lifecycle = new ArrayList<>();
                        for (String lifecycleMethod : Arrays.asList("initMethod", "destroyMethod")) {
                            Expression methodName = attribute(bean, lifecycleMethod);
                            if (methodName != null && !J.Literal.isLiteralValue(methodName, "")) {
                                lifecycle.add(lifecycleMethod + " = " + methodName.printTrimmed(getCursor()));
                            }
                        }
                        cdiAnnotations.add(lifecycle.isEmpty() ? produces : SearchResult.found(produces,
                                "Spring called `" + String.join(", ", lifecycle) + "` on this bean; call it from the producer or a `@Disposes` method"));
                        maybeAddImport(PRODUCES_FQN);
                        List<Expression> names = elements(attribute(bean, "value", "name"));
                        if (!names.isEmpty()) {
                            cdiAnnotations.add(annotation(NAMED_FQN, names.get(0), separator));
                            maybeAddImport(NAMED_FQN);
                        }
                        cdiAnnotations.add(annotation(cdiScope, null, separator));
                        maybeAddImport(cdiScope);

                        maybeRemoveImport(BEAN_FQN);
                        maybeRemoveImport(SCOPE_FQN);
                        maybeRemoveImport(CONFIGURABLE_BEAN_FACTORY_FQN);
                        maybeRemoveImport("org.springframework.context.annotation.ScopedProxyMode");
                        maybeRemoveImport("org.springframework.web.context.WebApplicationContext");
                        List<J.Annotation> converted = ListUtils.flatMap(annotations, a -> a == bean ? cdiAnnotations : a == scope ? null : a);
                        return m.withLeadingAnnotations(ListUtils.mapFirst(converted, a -> a.withPrefix(annotations.get(0).getPrefix())));
                    }
                });
    }

    static boolean isSpringSecurityBean(J.MethodDeclaration method) {
        JavaType.FullyQualified returnType = method.getReturnTypeExpression() == null ? null :
                TypeUtils.asFullyQualified(method.getReturnTypeExpression().getType());
        return returnType != null && returnType.getFullyQualifiedName().startsWith("org.springframework.security.") &&
               find(method.getLeadingAnnotations(), BEAN_MATCHER) != null;
    }

    static @Nullable String unconvertibleConfigurationReason(J.ClassDeclaration classDecl) {
        for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
            if (type != null && ("org.springframework.context.annotation.Conditional".equals(type.getFullyQualifiedName()) ||
                                 type.getFullyQualifiedName().startsWith("org.springframework.boot.") && type.getClassName().startsWith("ConditionalOn"))) {
                return CONDITIONAL_CONFIGURATION;
            }
        }
        for (Statement statement : classDecl.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration && isSpringSecurityBean((J.MethodDeclaration) statement)) {
                return SPRING_SECURITY;
            }
        }
        return null;
    }

    static J.Annotation annotation(String fqn, @Nullable Expression argument, Space prefix) {
        J.Annotation annotation = new J.Annotation(randomId(), prefix, Markers.EMPTY,
                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), fqn.substring(fqn.lastIndexOf('.') + 1), JavaType.ShallowClass.build(fqn), null),
                null);
        return argument == null ? annotation : annotation.withArguments(singletonList(argument.withPrefix(Space.EMPTY)));
    }

    private static J.@Nullable Annotation find(List<J.Annotation> annotations, AnnotationMatcher matcher) {
        for (J.Annotation annotation : annotations) {
            if (matcher.matches(annotation)) {
                return annotation;
            }
        }
        return null;
    }

    private static @Nullable Expression attribute(J.Annotation annotation, String... names) {
        List<String> attributeNames = Arrays.asList(names);
        for (Expression argument : annotation.getArguments() == null ? Collections.<Expression>emptyList() : annotation.getArguments()) {
            if (argument instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) argument;
                if (assignment.getVariable() instanceof J.Identifier &&
                    attributeNames.contains(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                    return assignment.getAssignment();
                }
            } else if (!(argument instanceof J.Empty) && attributeNames.contains("value")) {
                return argument;
            }
        }
        return null;
    }

    private static List<Expression> elements(@Nullable Expression expression) {
        if (expression instanceof J.NewArray) {
            List<Expression> initializer = ((J.NewArray) expression).getInitializer();
            return initializer == null ? emptyList() : ListUtils.filter(initializer, e -> !(e instanceof J.Empty));
        }
        return expression == null ? emptyList() : singletonList(expression);
    }
}
