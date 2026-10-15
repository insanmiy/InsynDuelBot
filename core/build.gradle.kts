plugins {
    `java-library`
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    inputs.property("version", rootProject.version)
    filesMatching("plugin.yml") {
        expand("version" to rootProject.version, "minecraftVersion" to "1.20")
    }
}

tasks.test {
    useJUnitPlatform()
}
