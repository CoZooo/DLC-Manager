import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.Locale
import java.util.Properties

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

layout.buildDirectory.set(file("../../build/dlc-javafx-manager"))

fun rootGradleProperty(key: String): String? {
    findProperty(key)?.let { return it.toString() }
    val rootPropsFile = file("../../gradle.properties")
    if (!rootPropsFile.exists()) {
        return null
    }
    val rootProps = Properties()
    rootPropsFile.inputStream().use(rootProps::load)
    return rootProps.getProperty(key)
}

fun String.capitalized(): String =
    replaceFirstChar { it.uppercase(Locale.ROOT) }

val fileOpsHelperVersion = rootGradleProperty("file_ops_helper_version").orEmpty()
val appVersion = rootGradleProperty("mod_version") ?: "0.0.0"
val releaseOutputDir = layout.projectDirectory.dir("../../output/$appVersion")
val javafxRuntimeJdkVersion = 25
version = appVersion

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javafxRuntimeJdkVersion))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val reusedCommonJavaFiles = listOf(
    "com/cozooo/dlc_manager/core/DlcFileMatch.java",
    "com/cozooo/dlc_manager/core/DlcItemFiles.java",
    "com/cozooo/dlc_manager/core/DlcResourceHistory.java",
    "com/cozooo/dlc_manager/bootstrap/DlcEarlyInstaller.java",
    "com/cozooo/dlc_manager/client/core/DlcZipUtil.java",
    "com/cozooo/dlc_manager/client/download/DlcCurseForgeClient.java",
    "com/cozooo/dlc_manager/client/download/DlcDownloadCandidate.java",
    "com/cozooo/dlc_manager/client/download/DlcDownloadHeaderUtil.java",
    "com/cozooo/dlc_manager/client/download/DlcDownloadService.java",
    "com/cozooo/dlc_manager/client/download/DlcDownloadUtil.java",
    "com/cozooo/dlc_manager/client/download/DlcModrinthClient.java",
    "com/cozooo/dlc_manager/client/download/DlcSharedService.java",
    "com/cozooo/dlc_manager/client/helper/DlcFileOpsHelperClient.java",
    "com/cozooo/dlc_manager/client/notice/DlcNoticeConfig.java",
    "com/cozooo/dlc_manager/client/notice/DlcNoticeRuntime.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackArchive.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackCFResolver.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackDcFiles.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackInstallPlan.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackInstaller.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackManifest.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackMeta.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackMetadata.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackOrder.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackPackage.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackRecovery.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackTransaction.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackUpdateCoordinator.java",
    "com/cozooo/dlc_manager/client/pack/DlcPackUpdateGuard.java",
    "com/cozooo/dlc_manager/client/preset/DlcPresetEntry.java",
    "com/cozooo/dlc_manager/client/preset/DlcPresetLoader.java",
    "com/cozooo/dlc_manager/client/preset/DlcPresetRuntimeState.java",
    "com/cozooo/dlc_manager/client/preset/DlcPresetStateStore.java",
    "com/cozooo/dlc_manager/client/preset/DlcPresetStatus.java",
    "com/cozooo/dlc_manager/client/remote/DlcRedirectEntry.java",
    "com/cozooo/dlc_manager/client/remote/DlcRedirectLoader.java",
    "com/cozooo/dlc_manager/client/remote/DlcRemoteEntry.java",
    "com/cozooo/dlc_manager/client/remote/DlcRemoteService.java",
    "com/cozooo/dlc_manager/client/remote/DlcRemoteLoader.java",
    "com/cozooo/dlc_manager/client/remote/DlcRemoteMarker.java",
    "com/cozooo/dlc_manager/client/remote/DlcRemoteRuntime.java",
    "com/cozooo/dlc_manager/client/required/DlcRequiredDependency.java",
    "com/cozooo/dlc_manager/client/required/DlcRequiredEntry.java",
    "com/cozooo/dlc_manager/client/required/DlcRequiredService.java",
    "com/cozooo/dlc_manager/client/required/DlcRequiredLoader.java",
    "com/cozooo/dlc_manager/client/required/DlcRequiredRuntime.java",
    "com/cozooo/dlc_manager/client/required/DlcRequiredRuntimeState.java",
    "com/cozooo/dlc_manager/client/selfupdate/DlcSelfUpdateService.java",
    "com/cozooo/dlc_manager/client/server/DlcServerCache.java",
    "com/cozooo/dlc_manager/client/server/DlcServerConnection.java",
    "com/cozooo/dlc_manager/client/server/DlcServerEntry.java",
    "com/cozooo/dlc_manager/client/server/DlcServerFileEntry.java",
    "com/cozooo/dlc_manager/client/server/DlcServerInfo.java",
    "com/cozooo/dlc_manager/client/server/DlcServerItem.java",
    "com/cozooo/dlc_manager/client/server/DlcServerPackApplyStatus.java",
    "com/cozooo/dlc_manager/client/server/DlcServerPackInfo.java",
    "com/cozooo/dlc_manager/client/server/DlcServerPresetItem.java",
    "com/cozooo/dlc_manager/client/server/DlcServerRedirectItem.java",
    "com/cozooo/dlc_manager/client/server/DlcServerRconConfig.java",
    "com/cozooo/dlc_manager/client/server/DlcServerRconInfo.java",
    "com/cozooo/dlc_manager/client/server/DlcServerRemoteItem.java",
    "com/cozooo/dlc_manager/client/server/DlcServerRemoteStatus.java",
    "com/cozooo/dlc_manager/client/server/DlcServerRequiredItem.java",
    "com/cozooo/dlc_manager/client/server/DlcServerRequiredProgressItem.java",
    "com/cozooo/dlc_manager/client/server/DlcServerRequiredStatus.java",
    "com/cozooo/dlc_manager/client/server/DlcServerSession.java",
    "com/cozooo/dlc_manager/client/server/DlcServerUpdateInfo.java",
    "com/cozooo/dlc_manager/client/server/DlcServerUpdateState.java",
    "com/cozooo/dlc_manager/client/server/DlcServerVersionUtil.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateDcParser.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateManifest.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateMeta.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdatePackage.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateRowPlanner.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateRuntime.java",
    "com/cozooo/dlc_manager/client/update/DlcPackReleaseUpdateService.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateSkipStore.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateService.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateVersion.java",
    "com/cozooo/dlc_manager/core/DlcApplier.java",
    "com/cozooo/dlc_manager/core/DlcApplyMode.java",
    "com/cozooo/dlc_manager/core/DlcConfig.java",
    "com/cozooo/dlc_manager/core/DlcConfigTypeSupport.java",
    "com/cozooo/dlc_manager/core/DlcDcConfigIO.java",
    "com/cozooo/dlc_manager/core/DlcDcFormatUtil.java",
    "com/cozooo/dlc_manager/core/DlcItem.java",
    "com/cozooo/dlc_manager/core/DlcPathUtil.java",
    "com/cozooo/dlc_manager/core/DlcPaths.java",
    "com/cozooo/dlc_manager/core/DlcScanner.java",
    "com/cozooo/dlc_manager/core/DlcRuntimeState.java",
    "com/cozooo/dlc_manager/core/DlcStateConfig.java",
    "com/cozooo/dlc_manager/core/DlcTmpPaths.java",
    "com/cozooo/dlc_manager/core/DlcTooltipLoader.java",
    "com/cozooo/dlc_manager/core/DlcType.java",
    "com/cozooo/dlc_manager/platform/DlcPlatformServices.java",
    "com/cozooo/dlc_manager/platform/PlatformServices.java",
)

sourceSets {
    main {
        java {
            exclude(reusedCommonJavaFiles)
        }
    }
}

val javafxRuntimeVersion = "25.0.3"

javafx {
    version = javafxRuntimeVersion
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

data class JavaFxFatJarPlatform(
    val javafxClassifier: String,
    val archiveClassifier: String,
    val label: String,
)

val javafxRuntimeModules = listOf("javafx-base", "javafx-graphics", "javafx-controls", "javafx-fxml")
val javafxFatJarPlatforms = mapOf(
    "win" to JavaFxFatJarPlatform("win", "windows", "Windows"),
    "linux" to JavaFxFatJarPlatform("linux", "linux", "Linux x64"),
    "linuxAarch64" to JavaFxFatJarPlatform("linux-aarch64", "linux-aarch64", "Linux aarch64"),
    "macX64" to JavaFxFatJarPlatform("mac", "mac-x64", "macOS x64"),
    "macAarch64" to JavaFxFatJarPlatform("mac-aarch64", "mac-aarch64", "macOS aarch64"),
)

val javafxRuntimeConfigurations = javafxFatJarPlatforms.mapKeys { (platformName, _) ->
    "javafx${platformName.capitalized()}Runtime"
}.mapValues { (configurationName, _) ->
    configurations.create(configurationName) {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}

dependencies {
    implementation("com.cozooo:dclanguage:0.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
    implementation("com.cozooo.dlc_manager:dlc_fileops_helper:$fileOpsHelperVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    javafxFatJarPlatforms.forEach { (platformName, platform) ->
        val configurationName = "javafx${platformName.capitalized()}Runtime"
        javafxRuntimeModules.forEach { moduleName ->
            add(configurationName, "org.openjfx:$moduleName:$javafxRuntimeVersion:${platform.javafxClassifier}") {
                isTransitive = false
            }
        }
    }
}

application {
    mainClass.set("com.cozooo.dlc_manager.app.DlcFxLauncher")
}

val jpackageLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(javafxRuntimeJdkVersion))
}

val appJvmArgs = mutableListOf("-Dfile.encoding=UTF-8")
if (javafxRuntimeJdkVersion >= 24) {
    appJvmArgs += "--sun-misc-unsafe-memory-access=allow"
}
val classpathJvmArgs = appJvmArgs + listOf("--enable-native-access=ALL-UNNAMED")
val moduleJvmArgs = appJvmArgs + listOf("--enable-native-access=javafx.graphics")
val hybridJvmArgs = appJvmArgs + listOf("--enable-native-access=javafx.graphics,ALL-UNNAMED")

application {
    applicationDefaultJvmArgs = classpathJvmArgs
}

distributions {
    main {
        contents {
            from(files("README.md", "README.zh.md")) {
                into("")
            }
            from(files("../../LICENSE", "../../LICENSING.md", "../../LICENSING.zh.md")) { into("") }
        }
    }
}

tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    from("../../LICENSE") { into("META-INF/dlc-manager") }
    from("../../LICENSING.md") { into("META-INF/dlc-manager") }
    from("../../LICENSING.zh.md") { into("META-INF/dlc-manager") }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs(classpathJvmArgs)
}

tasks.named<JavaCompile>("compileJava") {
    source(fileTree("../../src/main/java") {
        include(reusedCommonJavaFiles)
    })
}

tasks.named<Jar>("jar") {
    exclude("rebel.xml")
}

tasks.named<Zip>("distZip") {
    destinationDirectory.set(releaseOutputDir)
}

tasks.named<Tar>("distTar") {
    destinationDirectory.set(releaseOutputDir)
}

fun nonJavaFxRuntimeClasspath(): Set<File> =
    configurations.runtimeClasspath.get()
        .filter { file -> !file.name.startsWith("javafx-") }
        .toSet()

fun registerPlatformFatJar(
    taskName: String,
    classifier: String,
    label: String,
    javafxRuntime: Configuration,
) {
    tasks.register<Jar>(taskName) {
        group = "distribution"
        description = "Build a runnable fat jar for $label."
        dependsOn(tasks.named("classes"))
        dependsOn(configurations.runtimeClasspath, javafxRuntime)
        archiveBaseName.set("DLC-Manager")
        archiveVersion.set(appVersion)
        archiveClassifier.set(classifier)
        destinationDirectory.set(releaseOutputDir)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isZip64 = true

        manifest {
            attributes(
                "Main-Class" to application.mainClass.get(),
                "Implementation-Title" to "DLC Manager",
                "Implementation-Version" to appVersion,
            )
        }

        from(sourceSets.main.get().output)
        from({
            (nonJavaFxRuntimeClasspath() + javafxRuntime.files).map { file ->
                if (file.isDirectory) file else zipTree(file)
            }
        })
        exclude("rebel.xml")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

tasks.named<JavaExec>("run") {
    jvmArgs(hybridJvmArgs)
}

fun jpackageBin(): File {
    val binDir = jpackageLauncher.get().executablePath.asFile.parentFile
    val executableName = if (System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("windows")) {
        "jpackage.exe"
    } else {
        "jpackage"
    }
    return binDir.resolve(executableName)
}

val appIconPngFile = file("src/main/resources/assets/dlc_manager/textures/gui/icon.png")
val appIconIcnsFile = file("src/main/resources/assets/dlc_manager/textures/gui/icon.icns")
val appIconIcoFile = file("src/main/resources/assets/dlc_manager/textures/gui/icon.ico")
val appDisplayName = "DLC Manager"
val appIdentifier = "com.cozooo.dlcmanager"

javafxFatJarPlatforms.forEach { (platformName, platform) ->
    registerPlatformFatJar(
        "jar${platformName.capitalized()}",
        platform.archiveClassifier,
        platform.label,
        javafxRuntimeConfigurations.getValue("javafx${platformName.capitalized()}Runtime"),
    )
}

tasks.register("jarAll") {
    group = "distribution"
    description = "Build runnable fat jars for all supported JavaFX platforms."
    dependsOn(javafxFatJarPlatforms.keys.map { platformName ->
        tasks.named("jar${platformName.capitalized()}")
    })
}

val jpackageNativeImageDir = releaseOutputDir.dir("javafx-native-image")

tasks.register<Delete>("cleanJpackageImage") {
    delete(jpackageNativeImageDir)
}

tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Build a native application image with bundled JRE."
    dependsOn("installDist", "cleanJpackageImage")
    outputs.dir(jpackageNativeImageDir)

    val cmd = jpackageBin()
    if (!cmd.exists()) {
        throw GradleException("jpackage not found. Install JDK $javafxRuntimeJdkVersion+ or configure Gradle toolchains.")
    }
    val appDir = layout.buildDirectory.dir("install/${project.name}").get().asFile
    val mainJarName = tasks.named<Jar>("jar").get().archiveFileName.get()
    val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
    val isMacOs = osName.contains("mac")
    var packageIconFile = appIconPngFile
    if (isMacOs && appIconIcnsFile.exists()) {
        packageIconFile = appIconIcnsFile
    } else if (osName.contains("windows") && appIconIcoFile.exists()) {
        packageIconFile = appIconIcoFile
    }

    executable = cmd.absolutePath
    args(
        "--name", appDisplayName,
        "--input", appDir.resolve("lib").toString(),
        "--main-jar", mainJarName,
        "--main-class", application.mainClass.get(),
        "--app-version", appVersion,
        "--type", "app-image",
        "--dest", jpackageNativeImageDir.asFile.absolutePath,
    )
    if (packageIconFile.exists()) {
        args("--icon", packageIconFile.absolutePath)
    }
    args(
        "--module-path", appDir.resolve("lib").toString(),
        "--add-modules", "javafx.controls,javafx.fxml,javafx.graphics,java.desktop",
    )
    moduleJvmArgs.forEach { option ->
        args("--java-options", option)
    }
    if (isMacOs) {
        args("--mac-package-identifier", appIdentifier)
        args("--mac-package-name", appDisplayName)
    }
    args("--verbose")
}

tasks.register<Zip>("jpackageZip") {
    group = "distribution"
    description = "Zip the native application image."
    dependsOn("jpackageImage")
    archiveBaseName.set("${project.name}-native")
    from(jpackageNativeImageDir)
    destinationDirectory.set(releaseOutputDir)
}
