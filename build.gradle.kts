import java.util.zip.ZipFile

plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.22"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}
val minecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("1.21.4")

group = "dev.insanmiy"
version = "1.9.11"
repositories { mavenCentral() }
dependencies {
    implementation("net.kyori:adventure-text-serializer-legacy:4.20.0")
    paperweight.paperDevBundle("$minecraftVersion-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
tasks {
    test {
        useJUnitPlatform()
        classpath += sourceSets.main.get().compileClasspath
    }
    jar { archiveClassifier.set("paper-$minecraftVersion") }
    processResources {
        inputs.property("pluginVersion", project.version)
        inputs.property("minecraftVersion", minecraftVersion)
        filesMatching("plugin.yml") { expand("version" to project.version, "minecraftVersion" to minecraftVersion) }
    }
    runServer { minecraftVersion(minecraftVersion) }
}

// A separate test-only plugin exercises the real server. Never bundled in the release JAR.
tasks.withType<JavaCompile>().configureEach {
    options.isIncremental = false
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val integration = sourceSets.create("integration") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}
val integrationJar = tasks.register<Jar>("integrationJar") {
    archiveClassifier.set("integration-tests")
    from(integration.output)
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}
val verifyReleaseJar = tasks.register("verifyReleaseJar") {
    group = "verification"
    description = "Checks release metadata and rejects test fixtures or bundled server classes."
    val releaseJar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(releaseJar)
    dependsOn(tasks.jar)
    doLast {
        ZipFile(releaseJar.get().asFile).use { archive ->
            val entries = archive.entries().asSequence().map { it.name }.toList()
            check(entries.filter { it.endsWith(".yml") }.toSet() == setOf("config.yml", "plugin.yml", "kits.yml", "customkits.yml")) {
                "Release must contain the production configuration and plugin YAML files."
            }
            check(entries.none {
                it.startsWith("dev/insanmiy/practiceplugin/integration/") ||
                    it.startsWith("org/bukkit/") || it.startsWith("net/minecraft/")
            }) { "Release must not bundle test harnesses or server classes." }
            val metadata = archive.getInputStream(archive.getEntry("plugin.yml"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            check(metadata.contains("version: '${project.version}'")) { "Incorrect release version." }
        }
    }
}

tasks.check {
    dependsOn(integration.classesTaskName, verifyReleaseJar)
}

tasks.register<xyz.jpenilla.runpaper.task.RunServer>("runIntegrationServer") {
    group = "verification"
    description = "Runs destructive integration checks in run-integration only. Requires its EULA acceptance."
    minecraftVersion(minecraftVersion)
    runDirectory.set(layout.projectDirectory.dir("build/compat-servers/paper-$minecraftVersion/run-integration"))
    pluginJars(tasks.jar.flatMap { it.archiveFile }, integrationJar.flatMap { it.archiveFile })
    jvmArgs("-Xms512M", "-Xmx2G", "-Dinsyn.integration=true")
}

// Generate the small native API differences while retaining one combat implementation.
val nativeSources = layout.buildDirectory.dir("generated/compatibility/java")
val prepareCompatibilitySources = tasks.register("prepareCompatibilitySources") {
    inputs.dir("src/main/java")
    inputs.property("minecraftVersion", minecraftVersion)
    outputs.dir(nativeSources)
    doLast {
        val patch = minecraftVersion.split('.').getOrNull(2)?.toInt() ?: 0
        fileTree("src/main/java").matching { include("**/*.java") }.forEach { source ->
            var text = source.readText().replace("\r\n", "\n")
            if (patch >= 11 && source.name == "NmsBot.java") {
                text = text.replace("GameProfile profile = new GameProfile(UUID.randomUUID(), name);\n    profile\n        .getProperties()\n        .putAll(((CraftPlayer) owner).getHandle().getGameProfile().getProperties());",
                    "GameProfile profile = new GameProfile(UUID.randomUUID(), name, ((CraftPlayer) owner).getHandle().getGameProfile().properties());")
                text = text.replace("profile.getId()", "profile.id()")
                text = text.replace("handle.absMoveTo(", "handle.absSnapTo(")
                text = text.replace("new Vec3(motion.getXa(), motion.getYa(), motion.getZa())", "motion.getMovement()")
                text = text.replace("PacketSendListener listener", "io.netty.channel.ChannelFutureListener listener")
                text = text.replace("handle.setClientLoaded(true);", "handle.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());")
                text = text.replace("detectEquipmentUpdatesPublic()", "detectEquipmentUpdates()")
                text = text.replace("handle.isControlledByClient()", "true")
            }
            if (patch >= 11 && source.name == "BotLook.java")
                text = text.replace("broadcastAndSend(", "sendToTrackingPlayersAndSelf(")
            val target = nativeSources.get().file(source.relativeTo(file("src/main/java")).path).asFile
            target.parentFile.mkdirs()
            target.writeText(text)
        }
    }
}
sourceSets.main { java.setSrcDirs(listOf(nativeSources)) }
tasks.compileJava { dependsOn(prepareCompatibilitySources) }
tasks.register("writeCompileClasspath") { doLast { layout.buildDirectory.file("compile-classpath.txt").get().asFile.writeText(sourceSets.main.get().compileClasspath.asPath) } }

tasks.named<ProcessResources>("processIntegrationResources") {
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching("plugin.yml") { expand("minecraftVersion" to minecraftVersion) }
}
