plugins {
    kotlin("jvm") version "2.3.20"
    java
    application
}

group = "org.lightning323"
version = "1.0.3"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
// Jackson Core + TOML Support
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.17.0")
    // Allows Jackson to work with Kotlin Data Classes
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
// Source: https://mvnrepository.com/artifact/info.picocli/picocli
    implementation("info.picocli:picocli:4.7.7")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.lightning323.packInstaller.PackInstaller"
    }

    // Manually grab every dependency and shove it into the JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
application {
    mainClass.set("com.lightning323.packInstaller.PackInstaller")
}

sourceSets {
    main {
        java {
            // This tells Gradle to look for both Java and Kotlin files
            // in both directories during the compilation phase
            setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
        }
    }
}