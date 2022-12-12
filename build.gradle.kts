import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.5"
    id("io.spring.dependency-management") version "1.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.spring") version "1.7.20"
}

group = "no.nav.helse.flex"
version = "1"
description = "sykepengesoknad-backend"
java.sourceCompatibility = JavaVersion.VERSION_17

ext["okhttp3.version"] = "4.9.3" // For at token support testen kjører

val githubUser: String by project
val githubPassword: String by project

apply(plugin = "org.jlleitschuh.gradle.ktlint")

repositories {
    mavenCentral()

    maven {
        url = uri("https://maven.pkg.github.com/navikt/maven-release")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }

    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

val tokenSupportVersion = "3.0.2"
val smCommonVersion = "1.1e5e122"
val confluentVersion = "7.3.0"
val syfoKafkaVersion = "2021.07.20-09.39-6be2c52c"
val sykepengesoknadKafkaVersion = "2022.10.28-10.24-aa0eced7"
val mockitoKotlinVersion = "2.2.0"
val ojdbc8Version = "19.3.0.0"
val avroVersion = "1.11.1"
val logstashLogbackEncoderVersion = "7.2"
val testContainersVersion = "1.17.6"
val kluentVersion = "1.72"
val jsonSchemaValidatorVersion = "1.0.73"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("io.confluent:kafka-connect-avro-converter:$confluentVersion")
    implementation("io.confluent:kafka-schema-registry-client:$confluentVersion")
    implementation("org.flywaydb:flyway-core")
    implementation("org.slf4j:slf4j-api")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("no.nav.helse.flex:sykepengesoknad-kafka:$sykepengesoknadKafkaVersion")
    implementation("no.nav.syfo.kafka:kafkautils:$syfoKafkaVersion")
    implementation("no.nav.syfo.kafka:serialisering:$syfoKafkaVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")
    implementation("no.nav.security:token-client-spring:$tokenSupportVersion")
    implementation("org.aspectj:aspectjrt")
    implementation("org.aspectj:aspectjweaver")
    implementation("org.apache.httpcomponents:httpclient")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.testcontainers:kafka:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    testImplementation("org.awaitility:awaitility")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    this.archiveFileName.set("app.jar")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
        if (System.getenv("CI") == "true") {
            kotlinOptions.allWarningsAsErrors = true
        }
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("STARTED", "PASSED", "FAILED", "SKIPPED")
        exceptionFormat = FULL
    }
    failFast = false
}
