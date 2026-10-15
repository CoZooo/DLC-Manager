plugins { java }

group = "com.cozooo"
version = "0.1.1"

repositories { mavenCentral() }

val mpsHome = providers.gradleProperty("mpsHome").orElse(providers.environmentVariable("MPS_HOME"))
val mpsDirectory = file(mpsHome.getOrElse(".missing-mps-sdk"))
val mpsLibraries = fileTree(mpsDirectory.resolve("lib")) { include("**/*.jar") }

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

dependencies {
    if (providers.gradleProperty("dcLibraryJar").isPresent) {
        implementation(files(providers.gradleProperty("dcLibraryJar").get()))
        implementation("com.google.code.gson:gson:2.10.1")
    } else implementation("com.cozooo:dclanguage:0.1.1")
    compileOnly(mpsLibraries)
    testImplementation(mpsLibraries)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val verifyMpsSdk = tasks.register("verifyMpsSdk") {
    doLast { require(mpsDirectory.resolve("lib/mps-openapi.jar").isFile) { "Set -PmpsHome=/path/to/MPS or MPS_HOME to an MPS 2026.1 installation." } }
}
tasks.compileJava { dependsOn(verifyMpsSdk) }

val prepareLibraries = tasks.register<Sync>("prepareLibraries") {
    from(configurations.runtimeClasspath) {
        rename { if (it.startsWith("gson-")) "gson.jar" else "dclanguage.jar" }
    }
    into("languages/dc/lib")
}

val prepareLanguage = tasks.register<Sync>("prepareLanguage") {
    dependsOn(tasks.classes, prepareLibraries)
    from(sourceSets.main.get().output.classesDirs)
    into("languages/dc/classes_gen")
}

tasks.test {
    dependsOn(prepareLanguage)
    inputs.dir("languages")
    inputs.dir("solutions")
    inputs.file(".mps/modules.xml")
    inputs.dir("../src/test/resources/compatibility")
    exclude("**/DcPackagedPluginTest.class")
    useJUnitPlatform()
    maxParallelForks = 1
    systemProperty("mps.home", mpsDirectory.absolutePath)
    systemProperty("idea.home.path", mpsDirectory.absolutePath)
    systemProperty("idea.config.path", layout.buildDirectory.dir("mps/config").get().asFile.absolutePath)
    systemProperty("idea.system.path", layout.buildDirectory.dir("mps/system").get().asFile.absolutePath)
    systemProperty("idea.log.path", layout.buildDirectory.dir("mps/log").get().asFile.absolutePath)
    systemProperty("dc.mps.project", projectDir.absolutePath)
    jvmArgs("-Djava.awt.headless=true", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED")
    maxHeapSize = "2g"
}

val pluginZip = tasks.register<Zip>("pluginZip") {
    inputs.file("build.gradle.kts")
    dependsOn(tasks.jar, prepareLanguage)
    archiveFileName.set("dclanguage-mps-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("dclanguage") {
        from("../LICENSE")
        from("../THIRD_PARTY_NOTICES.md")
        from("../licenses") { into("licenses") }
    }
    into("dclanguage/lib") { from(tasks.jar); from(configurations.runtimeClasspath) }
    into("dclanguage/languages") {
        from("languages") {
            exclude("**/classes_gen/**", "**/lib/**")
            filesMatching("**/*.mpl") {
                filter {
                    it.replace("classes=\"mps\"", "classes=\"provided\"")
                        .replace(Regex("""<classes\b[^>]*/>"""), "")
                        .replace(Regex("""<library\b[^>]*/>"""), "")
                }
            }
        }
    }
}
tasks.assemble { dependsOn(pluginZip) }


val stagePlugin = tasks.register<Sync>("stagePlugin") {
    dependsOn(pluginZip)
    from(zipTree(pluginZip.get().archiveFile))
    into(layout.buildDirectory.dir("staged-plugin"))
}
val pluginTest = tasks.register<Test>("pluginTest") {
    dependsOn(stagePlugin, tasks.testClasses)
    inputs.dir(layout.buildDirectory.dir("staged-plugin"))
    inputs.dir("example")
    inputs.dir("definition-editor")
    inputs.dir("solutions")
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = files(sourceSets.test.get().output, configurations.testRuntimeClasspath.get().filter { !it.name.startsWith("dclanguage-") && !it.name.startsWith("dc-language-") })
    include("**/DcPackagedPluginTest.class")
    useJUnitPlatform()
    maxHeapSize = "2g"
    systemProperties(tasks.test.get().systemProperties)
    systemProperty("dc.mps.plugin", layout.buildDirectory.dir("staged-plugin/dclanguage").get().asFile.absolutePath)
    jvmArgs(tasks.test.get().jvmArgs!!)
}
tasks.test { finalizedBy(pluginTest) }

val definitionGenerator = sourceSets.create("definitionGenerator") {
    java.setSrcDirs(listOf("src/main/java"))
    java.include("com/cozooo/dclanguage/definition/**")
}
dependencies {
    add(definitionGenerator.implementationConfigurationName, "com.google.code.gson:gson:2.10.1")
    add(definitionGenerator.compileOnlyConfigurationName, mpsLibraries)
    add(definitionGenerator.runtimeOnlyConfigurationName, mpsLibraries)
}
tasks.named<JavaCompile>(definitionGenerator.compileJavaTaskName) { dependsOn(verifyMpsSdk) }
val prepareDefinitionLibraries = tasks.register<Sync>("prepareDefinitionLibraries") {
    from(configurations[definitionGenerator.runtimeClasspathConfigurationName]) { include("gson-*.jar"); rename { "gson.jar" } }
    into("languages/definition/lib")
}
val prepareDefinitionLanguage = tasks.register<Sync>("prepareDefinitionLanguage") {
    dependsOn(tasks.named(definitionGenerator.classesTaskName), prepareDefinitionLibraries)
    from(definitionGenerator.output.classesDirs)
    into("languages/definition/classes_gen")
}
prepareLanguage { dependsOn(prepareDefinitionLanguage) }

fun registerDefinitionGenerator(name: String, verify: Boolean) = tasks.register<JavaExec>(name) {
    dependsOn(prepareDefinitionLanguage)
    classpath = definitionGenerator.runtimeClasspath
    mainClass.set("com.cozooo.dclanguage.definition.GenerateLanguage")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    systemProperties(tasks.test.get().systemProperties)
    jvmArgs(tasks.test.get().jvmArgs!!)
    maxHeapSize = "2g"
    val definitionProject = providers.gradleProperty("definitionProject").getOrElse(projectDir.absolutePath)
    val destination = providers.gradleProperty("generatedOutput").getOrElse(file("../src/generated").absolutePath)
    args(definitionProject, destination)
    if (verify) args("--verify")
}
val generateLanguage = registerDefinitionGenerator("generateLanguage", false)
val verifyGeneratedLanguage = registerDefinitionGenerator("verifyGeneratedLanguage", true)
