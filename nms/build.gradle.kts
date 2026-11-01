subprojects {
    plugins.withId("java") {
        val adapterPackage = "dev.insanmiy.practiceplugin.nms.$name"
        val generateSharedNmsSources = tasks.register<Sync>("generateSharedNmsSources") {
            inputs.property("adapterPackage", adapterPackage)
            from(rootProject.layout.projectDirectory.dir("nms/shared/src/main/java")) {
                include("**/*.java")
                filteringCharset = "UTF-8"
                filter { line: String ->
                    line.replace("dev.insanmiy.practiceplugin.nms.shared", adapterPackage)
                }
                eachFile {
                    path = path.replace("/nms/shared/", "/nms/${project.name}/")
                }
                includeEmptyDirs = false
            }
            into(layout.buildDirectory.dir("generated/sources/sharedNms/main"))
        }

        extensions.configure<JavaPluginExtension> {
            sourceSets.named("main") {
                java.srcDir(generateSharedNmsSources)
            }
        }
    }
}
