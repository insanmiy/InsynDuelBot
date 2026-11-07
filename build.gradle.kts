import java.time.Duration

plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.22" apply false
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

allprojects {
    group = "dev.insanmiy"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
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

val verifyPluginJar = tasks.register("verifyPluginJar") {
    dependsOn(tasks.jar)
    doLast {
        val forbidden = listOf("net/minecraft/", "org/bukkit/craftbukkit/", "com/mojang/")
        zipTree(tasks.jar.get().archiveFile).visit {
            check(isDirectory || forbidden.none { relativePath.pathString.startsWith(it) }) {
                "Server Class Packaged Into Plugin JAR: $relativePath"
            }
        }
    }
}

tasks.check {
    dependsOn(verifyPluginJar)
}

tasks.named("build") {
    dependsOn(tasks.jar)
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("1.20.6")
val compatibilityTest = providers.gradleProperty("compatibilityTest").map { it.toBoolean() }.getOrElse(false)

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
    val dir = layout.projectDirectory.dir(
        if (compatibilityTest) "run/compatibility/$minecraftVersion" else "run/$minecraftVersion"
    )
    runDirectory.set(dir)
    args("--port", serverPort.toString(), "--nogui")
    if (compatibilityTest) {
        jvmArgs("-Dpractice.compatibility-test=true", "-Dterminal.jline=false", "-Dterminal.ansi=false")
        maxHeapSize = "1G"
        timeout.set(Duration.ofMinutes(5))
        doFirst {
            val result = dir.file("compatibility-result.txt").asFile
            check(!result.exists() || result.delete()) { "Could Not Remove The Previous Compatibility Result" }
            dir.file("server.properties").asFile.writeText(
                "server-ip=127.0.0.1\nonline-mode=false\nspawn-protection=0\n" +
                    "view-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\n" +
                    "generate-structures=false\nallow-nether=false\n" +
                    "generator-settings=" +
                    """{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}""" + "\n"
            )
            dir.file("bukkit.yml").asFile.writeText("settings:\n  allow-end: false\n")
        }
        doLast {
            val result = dir.file("compatibility-result.txt").asFile
            check(result.isFile && result.readText().trim() == "PASS") {
                "Minecraft $minecraftVersion Compatibility Checks Failed. See ${dir.asFile}/logs/latest.log"
            }
            val log = dir.file("logs/latest.log").asFile.readText()
            check(!Regex("NoSuchMethodError|AbstractMethodError|NoClassDefFoundError|EncoderException").containsMatchIn(log)
                && !Regex("(?m)^.*(?:ERROR|SEVERE).*\\[InsynDuelBot\\]").containsMatchIn(log)) {
                "Minecraft $minecraftVersion Logged Compatibility Errors. See ${dir.asFile}/logs/latest.log"
            }
        }
    }
    doFirst {
        val eulaFile = dir.file("eula.txt").asFile
        eulaFile.parentFile.mkdirs()
        eulaFile.writeText("eula=true\n")
    }
}
