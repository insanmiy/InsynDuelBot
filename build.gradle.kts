plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.22" apply false
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

allprojects {
    group = "dev.insanmiy"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}

tasks.jar {
    archiveFileName.set("InsynDuelBot_" + version + ".jar")

    from(project(":core").sourceSets.main.get().output)
    project(":nms").subprojects.forEach { from(it.sourceSets.main.get().output) }

    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}

val releaseJar = tasks.register("releaseJar") {
    dependsOn(tasks.jar)
}

tasks.named("build") {
    dependsOn(tasks.jar)
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("1.20.6")

val serverPort = providers.gradleProperty("serverPort").map { it.toInt() }.orElse(
    providers.provider {
        when (minecraftVersion) {
            "1.20.6" -> 25565
            "1.21", "1.21.0" -> 25566
            "1.21.1" -> 25567
            "1.21.2" -> 25568
            "1.21.3" -> 25569
            "1.21.4" -> 25570
            "1.21.5" -> 25571
            "1.21.6" -> 25572
            "1.21.7" -> 25573
            "1.21.8" -> 25574
            "1.21.9" -> 25575
            "1.21.10" -> 25576
            "1.21.11" -> 25577
            else -> 25565
        }
    }
).get()

tasks.runServer {
    minecraftVersion(minecraftVersion)
    val dir = layout.projectDirectory.dir("run/$minecraftVersion")
    runDirectory.set(dir)
    args("--port", serverPort.toString(), "--nogui")
    doFirst {
        val eulaFile = dir.file("eula.txt").asFile
        eulaFile.parentFile.mkdirs()
        eulaFile.writeText("eula=true\n")

        if (minecraftVersion.startsWith("1.20")) {
            val propsFile = dir.file("server.properties").asFile
            if (!propsFile.exists()) {
                propsFile.writeText("initial-enabled-packs=vanilla,update_1_21\n")
            } else {
                val content = propsFile.readText()
                if (content.contains("initial-enabled-packs=vanilla") && !content.contains("update_1_21")) {
                    propsFile.writeText(content.replace("initial-enabled-packs=vanilla", "initial-enabled-packs=vanilla,update_1_21"))
                }
            }
        }
    }
}
