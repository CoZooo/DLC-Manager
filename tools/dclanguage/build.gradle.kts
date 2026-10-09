import groovy.json.JsonSlurper
import java.security.MessageDigest

plugins {
    `java-library`
    kotlin("jvm") version "2.2.20"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "com.cozooo"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    api("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation(kotlin("stdlib"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    (options as StandardJavadocDocletOptions).addBooleanOption("notimestamp", true)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    from("LICENSE") { into("META-INF") }
    from("THIRD_PARTY_NOTICES.md") { into("META-INF/dclanguage") }
    from("licenses") { into("META-INF/dclanguage/licenses") }
}

tasks.named<Jar>("sourcesJar") {
    from(files(
        "SPEC.md", "SPEC.zh.md",
        "README.md", "README.zh.md",
        "DEVELOPMENT.md", "DEVELOPMENT.zh.md",
    )) { into("META-INF/dclanguage") }
    from("src/generated/generation-manifest.json") { into("META-INF/dclanguage") }
    from("mps/solutions/definition/models/dc.mps") { into("META-INF/dclanguage/mps") }
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates("com.cozooo", "dclanguage", project.version.toString())
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("DC Language")
        description.set("Standalone DC document parsing, schema validation, and lossless editing")
        url.set("https://github.com/CoZooo/DLC-Manager")
        licenses {
            license {
                name.set("DLC Manager Noncommercial Source License 1.0")
                url.set("https://github.com/CoZooo/DLC-Manager/blob/HEAD/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("CoZooo")
                name.set("CoZooo")
                url.set("https://github.com/CoZooo")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/CoZooo/DLC-Manager.git")
            developerConnection.set("scm:git:ssh://git@github.com/CoZooo/DLC-Manager.git")
            url.set("https://github.com/CoZooo/DLC-Manager")
        }
    }
}

sourceSets.main {
    java.srcDir("src/generated/java")
    resources.srcDir("src/generated/resources")
}

val verifyGeneratedLanguage = tasks.register("verifyGeneratedLanguage") {
    val generated = layout.projectDirectory.dir("src/generated")
    val definition = layout.projectDirectory.file("mps/solutions/definition/models/dc.mps")
    inputs.dir(generated)
    inputs.file(definition)
    doLast {
        fun digest(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val manifest = JsonSlurper().parse(generated.file("generation-manifest.json").asFile) as Map<*, *>
        check(manifest["definitionHash"] == digest(definition.asFile)) {
            "The MPS language definition changed. Run mps:generateLanguage with the MPS SDK before building."
        }
        val outputs = manifest["outputs"] as Map<*, *>
        for ((relative, expected) in outputs) {
            val file = generated.file(relative as String).asFile
            check(file.isFile && digest(file) == expected) {
                "Generated language output differs from its MPS generation manifest: $relative. Regenerate instead of editing generated files."
            }
        }
    }
}
tasks.compileJava { dependsOn(verifyGeneratedLanguage) }
tasks.processResources { dependsOn(verifyGeneratedLanguage) }
tasks.named("sourcesJar") { dependsOn(verifyGeneratedLanguage) }
