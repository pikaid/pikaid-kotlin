import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    application
}

kotlin {
    jvmToolchain(providers.gradleProperty("consumerToolchain").getOrElse("17").toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.pikaid:pikaid-kotlin:1.0.0")
}

application {
    mainClass.set("MainKt")
}
