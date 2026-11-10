import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.2.21"
    `java-library`
    `maven-publish`
    id("com.vanniktech.maven.publish.base") version "0.37.0"
    signing
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.dokka") version "2.3.0"
    id("org.jetbrains.dokka-javadoc") version "2.3.0"
}

group = "dev.pikaid"
version = "1.0.0"

val toolchainVersion = providers.gradleProperty("jvmToolchain").getOrElse("21").toInt()

kotlin {
    jvmToolchain(toolchainVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
        explicitApi()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("com.google.code.gson:gson:2.15.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kover {
    reports {
        verify {
            rule {
                bound {
                    minValue = 90
                }
            }
        }
    }
}

// Byte-for-byte reproducible archives: no entry timestamps and a stable entry order.
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// The library is Kotlin-only, so the standard `javadoc` task has no sources. Generate the
// Javadoc-format JAR from the Kotlin KDoc instead; Maven Central requires this classifier.
val dokkaJavadocJar by tasks.registering(Jar::class) {
    group = "documentation"
    description = "Javadoc JAR generated from the Kotlin KDoc."
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

// Test-only, self-contained bridge for the official conformance suite. It bundles the
// adapter, the library under test, and their runtime dependencies; it is never published.
val conformanceAdapterJar by tasks.registering(Jar::class) {
    group = "verification"
    description = "Builds the test-only JSON-Lines adapter for the official PikaID conformance suite."
    archiveFileName.set("pikaid-conformance-adapter.jar")
    destinationDirectory.set(layout.buildDirectory.dir("conformance"))
    manifest {
        attributes["Main-Class"] = "dev.pikaid.conformance.PikaIdJsonlAdapter"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(tasks.testClasses)
    from(sourceSets.main.get().output)
    from(sourceSets.test.get().output) { include("dev/pikaid/conformance/**") }
    from(
        configurations.testRuntimeClasspath
            .get()
            .filter { it.isFile }
            .map { dependency -> zipTree(dependency) },
    ) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
}

// Release archives must never contain local-only working artifacts such as `.ai/`.
val verifyArchiveHygiene by tasks.registering {
    group = "verification"
    description = "Fails if a distributable JAR contains local-only .ai entries."
    dependsOn(tasks.jar, tasks.named("sourcesJar"), dokkaJavadocJar)
    doLast {
        val archives = files(tasks.jar, tasks.named("sourcesJar"), dokkaJavadocJar).files
        archives.forEach { archive ->
            require(archive.isFile) { "expected release archive ${archive.path} to exist" }
            ZipFile(archive).use { zip ->
                val leaked =
                    zip
                        .entries()
                        .asSequence()
                        .map { it.name }
                        .filter { it == ".ai" || it.startsWith(".ai/") || it.contains("/.ai/") }
                        .toList()
                check(leaked.isEmpty()) { "${archive.name} contains local-only entries: $leaked" }
            }
        }
    }
}

tasks.check {
    dependsOn("ktlintCheck", "koverVerify", verifyArchiveHygiene)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(dokkaJavadocJar)
            pom {
                name.set("pikaid-kotlin")
                description.set("Official Kotlin implementation of the pikaid identifier format")
                url.set("https://github.com/pikaid/pikaid-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("pikaid")
                        name.set("pikaid")
                        email.set("larananas.dev@proton.me")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/pikaid/pikaid-kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/pikaid/pikaid-kotlin.git")
                    url.set("https://github.com/pikaid/pikaid-kotlin")
                }
            }
        }
    }
}


// Reuse the existing mavenJava publication, including the Dokka Javadoc JAR,
// and let the Central Portal validate and automatically release the bundle.
// The base plugin does not replace or duplicate the manually configured
// publication, and unsigned publishToMavenLocal remains available.
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
}

// Sign only when release credentials are supplied, so ordinary builds and
// `publishToMavenLocal` work without private keys. Supplying credentials enables signing
// for every publication (required by Maven Central).
signing {
    val signingKey =
        providers
            .gradleProperty("signingKey")
            .orElse(providers.environmentVariable("SIGNING_KEY"))
            .orNull
    val signingPassword =
        providers
            .gradleProperty("signingPassword")
            .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
            .orNull
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
