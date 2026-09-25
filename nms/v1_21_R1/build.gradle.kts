plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
}

dependencies {
    implementation(project(":core"))
    paperweight.paperDevBundle("1.21-R0.1-SNAPSHOT")
}
