@file:Suppress("UnstableApiUsage")
plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A collection of OpenRewrite recipes to assist with Spring to Quarkus migrations"

recipeDependencies {
    parserClasspath("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()

dependencies {
    annotationProcessor("org.projectlombok:lombok:latest.release")
    compileOnly("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite:rewrite-properties")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-migrate-java:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:${rewriteVersion}")

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite.gradle.tooling:model")
    testImplementation("org.openrewrite:rewrite-gradle")

    testRuntimeOnly("org.openrewrite:rewrite-java-21")
    testRuntimeOnly("org.springframework.boot:spring-boot:3.5.4")
    testRuntimeOnly("org.springframework.boot:spring-boot-autoconfigure:3.5.4")
    testRuntimeOnly("org.springframework.data:spring-data-jpa:3.5.4")
    testRuntimeOnly("org.springframework:spring-context:6.2.11")
    testRuntimeOnly("org.springframework:spring-beans:6.2.11")
    testRuntimeOnly("org.springframework:spring-web:6.2.11")
    testRuntimeOnly(gradleApi())
}
