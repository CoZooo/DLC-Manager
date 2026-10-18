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
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

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
    "com/cozooo/dlc_manager/bootstrap/DlcBootstrapI18n.java",
    "com/cozooo/dlc_manager/bootstrap/DlcModpackAutoUpdater.java",
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
    "com/cozooo/dlc_manager/client/required/DlcRequiredInstaller.java",
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
    "com/cozooo/dlc_manager/client/update/DlcPendingUpdate.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateJournal.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateCompatibility.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateDcParser.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateHashIndex.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateManifest.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateMeta.java",
    "com/cozooo/dlc_manager/client/update/DlcUpdateWorldBackup.java",
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
    "com/cozooo/dlc_manager/core/DlcDcDefinition.java",
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
    implementation("com.cozooo:dclanguage:0.1.1")
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
            from(files("../../LICENSE")) { into("") }
        }
    }
}

tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    from("../../LICENSE") { into("META-INF/dlc-manager") }
}

val bootstrapLanguages = tasks.register("bootstrapLanguages") {
    val sharedDirectory = file("../../src/main/resources/assets/dlc_manager/lang")
    val localDirectory = file("src/main/resources/assets/dlc_manager/lang")
    val languages = listOf("en_us", "zh_cn")
    val outputDirectory = layout.buildDirectory.dir("bootstrap-languages")
    inputs.files(languages.flatMap { listOf(sharedDirectory.resolve("$it.json"), localDirectory.resolve("$it.json")) })
    outputs.dir(outputDirectory)
    doLast {
        val output = outputDirectory.get().asFile.apply { mkdirs() }
        languages.forEach { language ->
            val local = JsonSlurper().parse(localDirectory.resolve("$language.json")) as Map<*, *>
            val shared = JsonSlurper().parse(sharedDirectory.resolve("$language.json")) as Map<*, *>
            val merged = local + shared.filterKeys { it.toString().startsWith("dlc_manager.bootstrap.") }
            output.resolve("$language.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(merged)) + "\n")
        }
    }
}

sourceSets.main {
    resources.exclude("assets/dlc_manager/lang/en_us.json", "assets/dlc_manager/lang/zh_cn.json")
}

tasks.named<ProcessResources>("processResources") {
    from(bootstrapLanguages) { into("assets/dlc_manager/lang") }
    from("../../src/main/resources/META-INF/dlc-manager/dlc-definition.json") {
        into("META-INF/dlc-manager")
        rename { "dlc-definition.json" }
    }
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
val nativeAppVersion = Regex("""^(\d+)\.(\d+)\.(\d+)(?:[-+][0-9A-Za-z.+-]+)?$""")
    .matchEntire(appVersion)?.groupValues?.drop(1)?.joinToString(".") { it.toInt().toString() }
    ?: throw GradleException("Installer versions require major.minor.patch, optionally followed by a prerelease suffix: $appVersion")
val nativeOs = System.getProperty("os.name", "").lowercase(Locale.ROOT).let {
    when {
        it.contains("windows") -> "windows"
        it.contains("mac") -> "mac"
        it.contains("linux") -> "linux"
        else -> "unsupported"
    }
}
val nativeArch = when (val arch = System.getProperty("os.arch", "").lowercase(Locale.ROOT)) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64" -> "x64"
    else -> arch
}
val nativePlatform = when (nativeOs) {
    "windows" -> if (nativeArch == "x64") "windows" else "windows-$nativeArch"
    "linux" -> if (nativeArch == "x64") "linux" else "linux-$nativeArch"
    else -> "$nativeOs-$nativeArch"
}
val installerFormats = mapOf("exe" to "windows", "msi" to "windows", "dmg" to "mac", "pkg" to "mac", "deb" to "linux", "rpm" to "linux")
val macSign = providers.gradleProperty("macSign").orElse("false").get().toBooleanStrict()
val macSigningIdentity = providers.environmentVariable("MAC_SIGNING_IDENTITY").orElse("").get()
val macSigningKeychain = providers.environmentVariable("MAC_SIGNING_KEYCHAIN").orElse("").get()
val macReleaseScript = file("packaging/macos/release.py")
val installerOutputDir = if (nativeOs == "mac" && macSign) releaseOutputDir.dir("signed") else releaseOutputDir

fun nativePackageCommand(type: String): List<String> {
    val command = listOf(jpackageBin().absolutePath) + nativePackageArguments(type)
    if (nativeOs != "mac" || !macSign) return command
    if (macSigningIdentity.isBlank()) throw GradleException("Set MAC_SIGNING_IDENTITY to the name and team in your Developer ID certificates.")
    return buildList {
        addAll(listOf("python3", macReleaseScript.absolutePath, "package", "--identity", macSigningIdentity,
            "--work-dir", layout.buildDirectory.dir("macos-signing").get().asFile.absolutePath))
        if (macSigningKeychain.isNotBlank()) addAll(listOf("--keychain", macSigningKeychain))
        add("--")
        addAll(command)
    }
}

fun nativePackageArguments(type: String): List<String> {
    val appDir = layout.buildDirectory.dir("install/${project.name}").get().asFile
    val packageOs = installerFormats[type] ?: nativeOs
    val icon = when (packageOs) {
        "mac" -> appIconIcnsFile
        "windows" -> appIconIcoFile
        else -> appIconPngFile
    }
    val arguments = mutableListOf(
        "--name", appDisplayName,
        "--input", appDir.resolve("lib").absolutePath,
        "--main-jar", tasks.named<Jar>("jar").get().archiveFileName.get(),
        "--main-class", application.mainClass.get(),
        "--app-version", nativeAppVersion,
        "--type", type,
        "--vendor", "CoZooo",
        "--description", "DLC Manager External Manager",
        "--module-path", appDir.resolve("lib").absolutePath,
        "--add-modules", "java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata,javafx.controls,javafx.fxml,javafx.graphics",
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files --bind-services",
    )
    if (icon.exists()) arguments += listOf("--icon", icon.absolutePath)
    if (type == "dmg") arguments += listOf("--resource-dir", file("packaging/macos").absolutePath)
    hybridJvmArgs.forEach { option -> arguments += listOf("--java-options", option) }
    if (packageOs == "mac") {
        arguments += listOf("--mac-package-identifier", appIdentifier, "--mac-package-name", appDisplayName)
        if (macSign) {
            arguments += listOf("--mac-sign", "--mac-signing-key-user-name", macSigningIdentity)
            if (macSigningKeychain.isNotBlank()) arguments += listOf("--mac-signing-keychain", macSigningKeychain)
        }
    }
    if (type != "app-image") {
        arguments += listOf("--about-url", "https://github.com/CoZooo/DLC-Manager")
        when (packageOs) {
            "windows" -> arguments += listOf(
                "--win-dir-chooser", "--win-shortcut", "--win-shortcut-prompt", "--win-menu",
                "--win-menu-group", appDisplayName,
                "--win-upgrade-uuid", UUID.nameUUIDFromBytes(appIdentifier.toByteArray(Charsets.UTF_8)).toString(),
            )
            "linux" -> arguments += listOf(
                "--linux-package-name", "dlc-manager", "--linux-shortcut",
                "--linux-package-deps", if (type == "deb") "libgtk-3-0 | libgtk-3-0t64, libasound2 | libasound2t64" else "gtk3, alsa-lib",
            )
        }
    }
    return arguments
}

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
    commandLine(nativePackageCommand("app-image"))
    args("--dest", jpackageNativeImageDir.asFile.absolutePath)
}

tasks.register<Zip>("jpackageZip") {
    group = "distribution"
    description = "Zip the native application image."
    dependsOn("jpackageImage")
    archiveBaseName.set("${project.name}-native")
    from(jpackageNativeImageDir)
    destinationDirectory.set(releaseOutputDir)
}

installerFormats.forEach { (format, requiredOs) ->
    tasks.register<Exec>("jpackage${format.capitalized()}") {
        group = "distribution"
        description = "Build a $format installer with bundled Java on $requiredOs."
        dependsOn("installDist")
        val hostOs = nativeOs
        val hostPlatform = nativePlatform
        val packageVersion = nativeAppVersion
        val supportedPlatforms = javafxFatJarPlatforms.values.map { it.archiveClassifier }
        val installerFile = installerOutputDir.file("DLC-Manager-$appVersion-$nativePlatform.$format")
        inputs.files(tasks.named("installDist"))
        inputs.files(appIconPngFile, appIconIcnsFile, appIconIcoFile)
        if (format == "dmg") inputs.dir(file("packaging/macos"))
        inputs.property("appVersion", appVersion)
        inputs.property("nativePlatform", nativePlatform)
        inputs.property("macSign", macSign)
        if (requiredOs == "mac" && macSign) {
            inputs.file(macReleaseScript)
            inputs.property("macSigningIdentity", macSigningIdentity)
            inputs.property("macSigningKeychain", macSigningKeychain)
            outputs.upToDateWhen { false }
        }
        inputs.file(jpackageLauncher.map { it.metadata.installationPath.file("release") })
        outputs.file(installerFile)
        commandLine(nativePackageCommand(format))
        doFirst {
            if (hostOs != requiredOs) throw GradleException("$format installers must be built on $requiredOs (current: $hostOs).")
            if (hostPlatform !in supportedPlatforms) {
                throw GradleException("Unsupported installer platform: $hostPlatform")
            }
            if (format in listOf("exe", "msi")) {
                val parts = packageVersion.split('.').map(String::toInt)
                if (parts[0] > 255 || parts[1] > 255 || parts[2] > 65535) {
                    throw GradleException("Windows installer versions require major/minor <= 255 and patch <= 65535: $packageVersion")
                }
            }
            Files.createDirectories(temporaryDir.toPath())
            val stagingDir = Files.createTempDirectory(temporaryDir.toPath(), "bundle-")
            args("--dest", stagingDir.toString())
        }
        doLast {
            val stagingDir = File(args[args.indexOf("--dest") + 1])
            val bundles = stagingDir.listFiles().orEmpty().filter { it.isFile && it.extension == format }
            if (bundles.size != 1) throw GradleException("Expected one .$format installer in $stagingDir; found ${bundles.size}.")
            Files.createDirectories(installerFile.asFile.parentFile.toPath())
            Files.copy(bundles.single().toPath(), installerFile.asFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.lifecycle("Installer: ${installerFile.asFile}")
        }
    }
}

tasks.register("jpackageInstaller") {
    group = "distribution"
    description = "Build the default installer for the current platform (Windows EXE, macOS DMG, Linux DEB)."
    val format = when (nativeOs) {
        "windows" -> "exe"
        "mac" -> "dmg"
        "linux" -> "deb"
        else -> throw GradleException("Unsupported installer operating system: $nativeOs")
    }
    dependsOn("jpackage${format.capitalized()}")
}

tasks.register("jpackageInstallers") {
    group = "distribution"
    description = "Build both installer formats supported by the current operating system."
    dependsOn(installerFormats.filterValues { it == nativeOs }.keys.map { "jpackage${it.capitalized()}" })
}

listOf("dmg", "pkg").forEach { format ->
    tasks.register<Exec>("notarize${format.capitalized()}") {
        group = "distribution"
        description = "Sign, notarize, and staple the macOS $format installer."
        dependsOn("jpackage${format.capitalized()}")
        val signingEnabled = nativeOs == "mac" && macSign
        val profile = providers.environmentVariable("MAC_NOTARY_PROFILE").orElse("").get()
        val keychain = providers.environmentVariable("MAC_NOTARY_KEYCHAIN").orElse("").get()
        val installerName = "DLC-Manager-$appVersion-$nativePlatform.$format"
        inputs.file(installerOutputDir.file(installerName))
        inputs.file(macReleaseScript)
        inputs.property("notaryProfile", profile)
        outputs.file(releaseOutputDir.dir("notarized").file(installerName))
        outputs.upToDateWhen { false }
        commandLine("python3", macReleaseScript.absolutePath, "notarize", "--profile", profile,
            "--output-dir", releaseOutputDir.dir("notarized").asFile.absolutePath,
            "--log-dir", layout.buildDirectory.dir("notarization").get().asFile.absolutePath,
            installerOutputDir.file(installerName).asFile.absolutePath)
        if (keychain.isNotBlank()) args("--keychain", keychain)
        doFirst {
            if (!signingEnabled) throw GradleException("Notarization requires macOS and -PmacSign=true.")
            if (profile.isBlank()) throw GradleException("Set MAC_NOTARY_PROFILE to your notarytool Keychain profile name.")
        }
    }
}

tasks.register("notarizeInstallers") {
    group = "distribution"
    description = "Build and notarize both macOS installer formats."
    dependsOn("notarizeDmg", "notarizePkg")
}
