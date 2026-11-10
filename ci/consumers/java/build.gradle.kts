plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(
            JavaLanguageVersion.of(providers.gradleProperty("consumerToolchain").getOrElse("17").toInt()),
        )
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.pikaid:pikaid-kotlin:${providers.gradleProperty("pikaidVersion").getOrElse("1.0.0")}")
}

application {
    mainClass.set("Main")
}
