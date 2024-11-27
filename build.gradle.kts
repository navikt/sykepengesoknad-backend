import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
}

group = "no.nav.helse.flex"
version = "1"
description = "sykepengesoknad-backend"
java.sourceCompatibility = JavaVersion.VERSION_21

ext["okhttp3.version"] = "4.12" // Token-support tester trenger MockWebServer.

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

val tokenSupportVersion = "5.0.13"
val confluentVersion = "7.7.1"
val syfoKafkaVersion = "2021.07.20-09.39-6be2c52c"
val sykepengesoknadKafkaVersion = "2024.10.21-14.49-932f4f94"
val mockitoKotlinVersion = "2.2.0"
val avroVersion = "1.12.0"
val logstashLogbackEncoderVersion = "8.0"
val testContainersVersion = "1.20.3"
val kluentVersion = "1.73"
val jsonSchemaValidatorVersion = "1.5.3"
val unleashVersion = "9.2.4"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.aspectj:aspectjrt")
    implementation("org.aspectj:aspectjweaver")
    implementation("org.apache.httpcomponents.client5:httpclient5")
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
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.3.5")

    testImplementation(platform("org.testcontainers:testcontainers-bom:$testContainersVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
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
        maxParallelForks =
            if (System.getenv("CI") == "true") {
                (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1).coerceAtMost(4)
            } else {
                2
            }
    }
}

tasks {
    bootJar {
        archiveFileName = "app.jar"
    }
}
