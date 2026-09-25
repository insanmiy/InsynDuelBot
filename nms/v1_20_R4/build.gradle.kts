plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
}

dependencies {
    implementation(project(":core"))
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
}
