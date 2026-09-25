pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
rootProject.name = "InsynDuelBot"

include("core")
include("nms:v1_20_R4")
include("nms:v1_21_R1")
include("nms:v1_21_R3")
