plugins { application }
group = "com.cozooo"
version = "0.1.0"
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
application { mainClass.set("com.cozooo.dclanguage.lsp.DcLanguageServer") }
dependencies {
    implementation("com.cozooo:dclanguage:0.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    useJUnitPlatform()
    doFirst { systemProperty("lsp.testClasspath", sourceSets.test.get().runtimeClasspath.asPath) }
}
val shadowJar = tasks.register<Jar>("shadowJar") {
    dependsOn(tasks.classes, configurations.runtimeClasspath)
    archiveFileName.set("dclanguage-lsp.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes["Main-Class"] = application.mainClass.get()
    from(sourceSets.main.get().output)
    from(provider { configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
}
tasks.register<Zip>("serverDistZip") {
    dependsOn(shadowJar)
    archiveFileName.set("dclanguage-lsp-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(shadowJar)
    from("launchers") { filePermissions { unix("rwxr-xr-x") } }
    from("README.md", "README.zh.md")
    from("../LICENSE")
    from("../THIRD_PARTY_NOTICES.md")
    from("../licenses") { into("licenses") }
}
