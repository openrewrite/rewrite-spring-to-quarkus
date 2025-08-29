@file:Suppress("UnstableApiUsage")
plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A template repository for creating new OpenRewrite modules"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    annotationProcessor("org.projectlombok:lombok:latest.release")
    compileOnly("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")

    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    implementation("io.quarkus:quarkus-update-recipes:latest.release")

    testImplementation("org.openrewrite:rewrite-test")
    testRuntimeOnly("org.openrewrite:rewrite-java-21")

    testRuntimeOnly("org.springframework:spring-web:latest.release")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}
