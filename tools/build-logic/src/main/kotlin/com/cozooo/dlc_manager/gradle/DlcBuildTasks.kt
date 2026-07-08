package com.cozooo.dlc_manager.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

private fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split(".").mapNotNull { it.toIntOrNull() }
    val rightParts = right.split(".").mapNotNull { it.toIntOrNull() }
    val size = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until size) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }
    return 0
}

private fun evaluateCondition(expression: String, loaderName: String, minecraftVersion: String): Boolean =
    expression.split("||").any { orPart ->
        orPart.split("&&").all { rawTerm ->
            var term = rawTerm.trim()
            if (term.isEmpty()) {
                return@all true
            }
            val negated = term.startsWith("!")
            if (negated) {
                term = term.drop(1).trim()
            }
            val value = when (term) {
                "fabric", "forge", "neoforge" -> loaderName == term
                "forgeLike" -> loaderName == "forge" || loaderName == "neoforge"
                "fabricLike" -> loaderName == "fabric"
                else -> {
                    val match = Regex("""^(>=|<=|>|<|==|=)?\s*([0-9]+(?:\.[0-9]+)*)$""").matchEntire(term)
                        ?: error("Unsupported Stonecutter condition '$rawTerm'")
                    val operator = match.groupValues[1].ifBlank { "==" }
                    val compared = compareVersions(minecraftVersion, match.groupValues[2])
                    when (operator) {
                        ">" -> compared > 0
                        ">=" -> compared >= 0
                        "<" -> compared < 0
                        "<=" -> compared <= 0
                        "=", "==" -> compared == 0
                        else -> false
                    }
                }
            }
            if (negated) !value else value
        }
    }

private fun preprocessStonecutterText(source: String, loaderName: String, minecraftVersion: String): String {
    data class Frame(var parentActive: Boolean, var branchMatched: Boolean, var currentActive: Boolean)

    val directivePattern = Regex("""^[*/^]*//\?\s*(.*)$""")
    val frames = mutableListOf<Frame>()
    val output = mutableListOf<String>()
    source.lineSequence().forEach { line ->
        val trimmed = line.trim()
        val directive = directivePattern.matchEntire(trimmed)?.groupValues?.get(1)
        val ifMatch = directive?.let { Regex("""^if\s+(.+?)\s*\{$""").matchEntire(it) }
        val elseIfMatch = directive?.let { Regex("""^\}\s*else\s+if\s+(.+?)\s*\{$""").matchEntire(it) }
        val elseMatch = directive?.let { Regex("""^\}\s*else\s*\{$""").matchEntire(it) }
        val endMatch = directive?.let { Regex("""^\}$""").matchEntire(it) }
        when {
            ifMatch != null -> {
                val parentActive = frames.lastOrNull()?.currentActive ?: true
                val active = parentActive && evaluateCondition(ifMatch.groupValues[1], loaderName, minecraftVersion)
                frames += Frame(parentActive, active, active)
            }
            elseIfMatch != null -> {
                val frame = frames.lastOrNull() ?: error("Stonecutter else-if without matching if")
                val active = frame.parentActive &&
                    !frame.branchMatched &&
                    evaluateCondition(elseIfMatch.groupValues[1], loaderName, minecraftVersion)
                frame.currentActive = active
                frame.branchMatched = frame.branchMatched || active
            }
            elseMatch != null -> {
                val frame = frames.lastOrNull() ?: error("Stonecutter else without matching if")
                val active = frame.parentActive && !frame.branchMatched
                frame.currentActive = active
                frame.branchMatched = true
            }
            endMatch != null -> {
                if (frames.isEmpty()) {
                    error("Stonecutter end without matching if")
                }
                frames.removeLast()
            }
            frames.lastOrNull()?.currentActive != false -> output += line
        }
    }
    check(frames.isEmpty()) { "Unclosed Stonecutter condition" }
    return output.joinToString(System.lineSeparator()) + System.lineSeparator()
}

private fun copyProcessedTree(sourceRoot: File, outputRoot: File, loaderName: String, minecraftVersion: String) {
    if (!sourceRoot.isDirectory) {
        return
    }
    sourceRoot.walkTopDown().forEach { input ->
        if (input.isDirectory) {
            return@forEach
        }
        val relative = sourceRoot.toPath().relativize(input.toPath()).toString().replace(File.separatorChar, '/')
        val output = outputRoot.resolve(relative)
        output.parentFile.mkdirs()
        if (input.extension in setOf("java", "kt", "json", "toml", "mcmeta", "properties", "txt", "md")) {
            output.writeText(preprocessStonecutterText(input.readText(), loaderName, minecraftVersion))
        } else {
            input.copyTo(output, overwrite = true)
        }
    }
}

abstract class GenerateDlcSourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainJavaDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainKotlinDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainResourcesDir: DirectoryProperty

    @get:Input
    abstract val loaderName: Property<String>

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputRoot = outputDir.get().asFile
        outputRoot.deleteRecursively()
        copyProcessedTree(mainJavaDir.get().asFile, outputRoot.resolve("java"), loaderName.get(), minecraftVersion.get())
        copyProcessedTree(mainKotlinDir.get().asFile, outputRoot.resolve("kotlin"), loaderName.get(), minecraftVersion.get())
        copyProcessedTree(mainResourcesDir.get().asFile, outputRoot.resolve("resources"), loaderName.get(), minecraftVersion.get())
    }
}

abstract class DeleteKotlinClassesReplacedByJavaTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSourceDir: DirectoryProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinClassesDir: DirectoryProperty

    @TaskAction
    fun deleteReplacedClasses() {
        val classesRoot = kotlinClassesDir.asFile.orNull ?: return
        if (!classesRoot.exists()) {
            return
        }
        val kotlinRoot = kotlinSourceDir.get().asFile
        if (!kotlinRoot.exists()) {
            return
        }
        kotlinRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { kotlinRoot.toPath().relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
            .map { it.replace(Regex("""\.kt$"""), ".java") }
            .forEach { javaPath ->
                val classPath = javaPath.removeSuffix(".java")
                val slash = classPath.lastIndexOf('/')
                val classDir = if (slash >= 0) classesRoot.resolve(classPath.substring(0, slash)) else classesRoot
                val className = if (slash >= 0) classPath.substring(slash + 1) else classPath
                if (classDir.exists()) {
                    classDir.listFiles { file ->
                        file.name == "$className.class" ||
                            (file.name.startsWith("${className}$") && file.name.endsWith(".class"))
                    }?.forEach(File::delete)
                }
            }
    }
}

abstract class PatchFabricModJsonTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fabricModJson: RegularFileProperty

    @get:Input
    abstract val patchEnabled: Property<Boolean>

    @get:Input
    abstract val fabricLanguageKotlinVersion: Property<String>

    @TaskAction
    fun patch() {
        if (!patchEnabled.get()) {
            return
        }
        val outputFile = fabricModJson.asFile.orNull ?: return
        if (!outputFile.exists()) {
            return
        }
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parse(outputFile) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val depends = json["depends"] as MutableMap<String, Any?>
        depends["fabric-language-kotlin"] = ">=${fabricLanguageKotlinVersion.get()}"
        outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n")
    }
}
