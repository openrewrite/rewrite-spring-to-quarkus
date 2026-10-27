@file:Suppress("UnstableApiUsage")
plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A collection of OpenRewrite recipes to assist with Spring to Quarkus migrations"

recipeDependencies {
    parserClasspath("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    parserClasspath("jakarta.inject:jakarta.inject-api:2.0.1")
    parserClasspath("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
    parserClasspath("org.eclipse.microprofile.config:microprofile-config-api:3.0.3")
    parserClasspath("io.quarkus:quarkus-core:3.28.2")
    parserClasspath("io.quarkus:quarkus-hibernate-orm-panache:3.28.2")
    parserClasspath("io.quarkus:quarkus-panache-common:3.28.2")
    parserClasspath("io.smallrye:smallrye-health-api:4.0.0")
    parserClasspath("io.smallrye.config:smallrye-config-core:3.4.0")
    parserClasspath("org.eclipse.microprofile.health:microprofile-health-api:4.0.1")
    parserClasspath("io.quarkus:quarkus-junit5:3.28.2")

    testParserClasspath("org.springframework.boot:spring-boot:3.5.4")
    testParserClasspath("org.springframework.boot:spring-boot-autoconfigure:3.5.4")
    testParserClasspath("org.springframework.boot:spring-boot-actuator:3.5.4")
    testParserClasspath("org.springframework.boot:spring-boot-test:3.5.4")
    testParserClasspath("org.springframework.boot:spring-boot-test-autoconfigure:3.5.4")
    testParserClasspath("org.springframework:spring-beans:6.2.11")
    testParserClasspath("org.springframework:spring-context:6.2.11")
    testParserClasspath("org.springframework:spring-core:6.2.11")
    testParserClasspath("org.springframework:spring-tx:6.2.11")
    testParserClasspath("org.springframework:spring-web:6.2.11")
    testParserClasspath("org.springframework.data:spring-data-commons:3.5.4")
    testParserClasspath("org.springframework.data:spring-data-jpa:3.5.4")
    testParserClasspath("org.springframework.data:spring-data-mongodb:4.4.4")
    testParserClasspath("org.springframework.cloud:spring-cloud-commons:4.1.4")
    testParserClasspath("jakarta.persistence:jakarta.persistence-api:3.1.0")
    testParserClasspath("jakarta.transaction:jakarta.transaction-api:2.0.1")
    testParserClasspath("io.quarkus:quarkus-hibernate-orm-panache:3.17.8")
    testParserClasspath("io.quarkus:quarkus-mongodb-panache:3.17.8")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()

dependencies {
    annotationProcessor("org.projectlombok:lombok:latest.release")
    compileOnly("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite:rewrite-properties")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-migrate-java:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:${rewriteVersion}")

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite.gradle.tooling:model")

    testRuntimeOnly("org.openrewrite:rewrite-java-21")
    testRuntimeOnly(gradleApi())
}
