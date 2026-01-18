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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateSpringAopTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.quarkus.spring.MigrateSpringAop"))
          .parser(JavaParser.fromJavaVersion()
            .classpath("aspectjweaver", "jakarta.interceptor-api"));
    }

    @DocumentExample
    @Test
    void convertAspectToInterceptor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.aspectj.lang.annotation.Aspect;
              import org.aspectj.lang.annotation.Around;
              import org.aspectj.lang.ProceedingJoinPoint;

              @Aspect
              public class LoggingAspect {
                  @Around("execution(* com.example.service.*.*(..))")
                  public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
                      System.out.println("Before method");
                      Object result = joinPoint.proceed();
                      System.out.println("After method");
                      return result;
                  }
              }
              """,
            """
              import jakarta.interceptor.AroundInvoke;
              import jakarta.interceptor.Interceptor;
              import jakarta.interceptor.InvocationContext;

              @Interceptor
              public class LoggingAspect {
                  @AroundInvoke("execution(* com.example.service.*.*(..))")
                  public Object logExecution(InvocationContext joinPoint) throws Throwable {
                      System.out.println("Before method");
                      Object result = joinPoint.proceed();
                      System.out.println("After method");
                      return result;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonAspectClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class RegularClass {
                  public void doSomething() {
                      System.out.println("Hello");
                  }
              }
              """
          )
        );
    }
}
