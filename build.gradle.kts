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
        attributes["Main-Class"] = "com.lightning323.packInstaller.installer.PackInstaller"
    }

    // Manually grab every dependency and shove it into the JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
// Create a brand new task specifically for the cross-platform wrapper
tasks.register<Jar>("wrapperJar") {
    group = "build"
    description = "Assembles a lightweight cross-platform wrapper jar for Prism Launcher."
    archiveFileName.set("wrapper.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.lightning323.packInstaller.wrapper.ModpackPostExitTracker"
    }

    // 1. Include the wrapper's own compiled classes
    from(sourceSets.main.get().output) {
        include("com/lightning323/packInstaller/wrapper/**")
    }

    // 2. Unpack and include ONLY Jackson dependencies into this JAR
    from(configurations.runtimeClasspath.get().map { file ->
        if (file.name.contains("jackson-core") || file.name.contains("jackson-databind") || file.name.contains("jackson-annotations")) {
            if (file.isDirectory) file else zipTree(file)
        } else {
            files() // Skip everything else
        }
    })
}

// Optional: Force the 'build' task to output BOTH jars automatically
tasks.build {
    dependsOn(tasks.named("wrapperJar"))
}

application {
    mainClass.set("com.lightning323.packInstaller.installer.PackInstaller")
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