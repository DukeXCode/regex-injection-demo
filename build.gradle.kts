val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.1.20"
    id("io.ktor.plugin") version "3.3.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
}

group = "ch.dukex"
version = "0.0.1"

application {
    mainClass.set("ch.dukex.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

kotlin {
    // Pin to JDK 21 (LTS, fully supported by Kotlin 2.1.20). Aligns the
    // Java and Kotlin compile targets so they don't disagree when the host
    // JDK (e.g. 25) is newer than what Kotlin can target.
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}
