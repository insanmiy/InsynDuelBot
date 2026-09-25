plugins {
    `java-library`
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    implementation("net.kyori:adventure-text-serializer-legacy:4.20.0")
}

tasks.processResources {
    inputs.property("version", rootProject.version)
    filesMatching("plugin.yml") {
        expand("version" to rootProject.version, "minecraftVersion" to "1.20")
    }
}
