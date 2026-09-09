import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
}

group = "no.nav.helse.flex"
version = "1"
description = "sykepengesoknad-backend"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

val tokenSupportVersion = "6.0.12"
val confluentVersion = "8.2.1"
val syfoKafkaVersion = "2021.07.20-09.39-6be2c52c"
val sykepengesoknadKafkaVersion = "2026.07.28-13.22-138bf702"
val mockitoKotlinVersion = "2.2.0"
val avroVersion = "1.12.2"
val logstashLogbackEncoderVersion = "9.0"
val testContainersVersion = "2.0.5"
val kluentVersion = "1.73"
val jsonSchemaValidatorVersion = "3.0.6"
val unleashVersion = "12.3.0"
val opentelemetryApiVersion = "1.65.0"
val opentelemetryInstrumentationVersion = "2.31.1"
val mockWebServerVersion = "5.5.0"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.aspectj:aspectjrt")
    implementation("org.aspectj:aspectjweaver")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")
    implementation("no.nav.security:token-client-spring:$tokenSupportVersion")
    implementation("no.nav.helse.flex:sykepengesoknad-kafka:$sykepengesoknadKafkaVersion")
    implementation("io.confluent:kafka-connect-avro-converter:$confluentVersion")
    implementation("io.confluent:kafka-schema-registry-client:$confluentVersion")
    implementation("no.nav.syfo.kafka:kafkautils:$syfoKafkaVersion")
    implementation("no.nav.syfo.kafka:serialisering:$syfoKafkaVersion")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("io.getunleash:unleash-client-java:$unleashVersion")
    implementation("io.opentelemetry:opentelemetry-api:$opentelemetryApiVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryInstrumentationVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-metrics-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testImplementation("com.squareup.okhttp3:mockwebserver3:$mockWebServerVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-kafka:$testContainersVersion")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("commons-codec:commons-codec")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
        if (System.getenv("CI") == "true") {
            allWarningsAsErrors.set(true)
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        testLogging {
            events("PASSED", "FAILED", "SKIPPED")
            exceptionFormat = FULL
        }
        failFast = false
        reports.html.required.set(false)
        reports.junitXml.required.set(false)
        maxParallelForks = 6
    }
}

tasks {
    bootJar {
        archiveFileName = "app.jar"
    }
}
