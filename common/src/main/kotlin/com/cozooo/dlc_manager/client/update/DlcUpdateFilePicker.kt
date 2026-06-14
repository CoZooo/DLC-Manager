package com.cozooo.dlc_manager.client.update

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.nio.Buffer
import java.nio.file.Files
import java.nio.file.Path

object DlcUpdateFilePicker {
    @JvmStatic
    fun pickUpdateZip(title: String?): Path? {
        return try {
            val osName = System.getProperty("os.name", "").lowercase()
            val tiny = tryTinyFileDialog(title)
            if (tiny.canceled) {
                return null
            }
            if (tiny.path != null) {
                return tiny.path
            }
            if (osName.contains("mac")) {
                val apple = tryAppleScriptFileDialog(title)
                if (apple.canceled) {
                    return null
                }
                if (apple.path != null) {
                    return apple.path
                }
                if (!tiny.available && !apple.available) {
                    openUpdateDir()
                } else if (!tiny.canceled && !apple.canceled) {
                    openUpdateDir()
                }
                return null
            }
            if (!tiny.available) {
                openUpdateDir()
            } else if (!tiny.canceled) {
                openUpdateDir()
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun pickAnyFile(title: String?): Path? {
        return try {
            val osName = System.getProperty("os.name", "").lowercase()
            val tiny = tryTinyFileDialogAny(title, "")
            if (tiny.canceled) {
                return null
            }
            if (tiny.path != null) {
                return tiny.path
            }
            if (osName.contains("mac")) {
                val apple = tryAppleScriptFileDialogAny(title, null)
                if (apple.canceled) {
                    return null
                }
                if (apple.path != null) {
                    return apple.path
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun pickAnyFile(defaultDir: Path?, title: String?): Path? {
        return try {
            val osName = System.getProperty("os.name", "").lowercase()
            val defaultPath = if (defaultDir == null) "" else defaultDir.toString() + File.separator
            val tiny = tryTinyFileDialogAny(title, defaultPath)
            if (tiny.canceled) {
                return null
            }
            if (tiny.path != null) {
                return tiny.path
            }
            if (osName.contains("mac")) {
                val apple = tryAppleScriptFileDialogAny(title, defaultDir)
                if (apple.canceled) {
                    return null
                }
                if (apple.path != null) {
                    return apple.path
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun pickSaveFile(title: String?, suggestedName: String?): Path? {
        return try {
            val osName = System.getProperty("os.name", "").lowercase()
            val tiny = tryTinySaveDialog(title, suggestedName)
            if (tiny.canceled) {
                return null
            }
            if (tiny.path != null) {
                return tiny.path
            }
            if (osName.contains("mac")) {
                val apple = tryAppleScriptSaveDialog(title, suggestedName)
                if (apple.canceled) {
                    return null
                }
                if (apple.path != null) {
                    return apple.path
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun pickFolder(title: String?): Path? {
        return try {
            val osName = System.getProperty("os.name", "").lowercase()
            val tiny = tryTinyFolderDialog(title)
            if (tiny.canceled) {
                return null
            }
            if (tiny.path != null) {
                return tiny.path
            }
            if (osName.contains("mac")) {
                val apple = tryAppleScriptFolderDialog(title)
                if (apple.canceled) {
                    return null
                }
                if (apple.path != null) {
                    return apple.path
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryTinyFileDialog(title: String?): PickResult {
        return try {
            val updateDir = DlcUpdateService.updateDir
            if (updateDir != null) {
                Files.createDirectories(updateDir)
            }
            val defaultPath = if (updateDir == null) "" else updateDir.toString() + File.separator
            val stackClass = Class.forName("org.lwjgl.system.MemoryStack")
            val memUtilClass = Class.forName("org.lwjgl.system.MemoryUtil")
            val pbClass = Class.forName("org.lwjgl.PointerBuffer")
            val tinyClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
            val stack = stackClass.getMethod("stackPush").invoke(null)
            try {
                val filters = stackClass.getMethod("mallocPointer", Int::class.javaPrimitiveType).invoke(stack, 1)
                val utf8Buffer = stackClass.getMethod("UTF8", CharSequence::class.java).invoke(stack, "*.zip")
                val addr = memUtilClass.getMethod("memAddress", Buffer::class.java).invoke(null, utf8Buffer) as Long
                pbClass.getMethod("put", Long::class.javaPrimitiveType).invoke(filters, addr)
                pbClass.getMethod("flip").invoke(filters)
                val target = findOpenFileDialog(tinyClass, pbClass) ?: return PickResult.unavailable()
                val allowMulti = allowMultiValue(target.parameterTypes[4])
                val result = target.invoke(
                    null,
                    title ?: "",
                    defaultPath,
                    filters,
                    "zip",
                    allowMulti
                )
                if (result is String && result.isNotBlank()) {
                    PickResult.picked(Path.of(result))
                } else {
                    PickResult.canceled()
                }
            } finally {
                closeStack(stackClass, stack)
            }
        } catch (_: Throwable) {
            PickResult.unavailable()
        }
    }

    private fun tryTinyFileDialogAny(title: String?, defaultPath: String?): PickResult {
        return try {
            val stackClass = Class.forName("org.lwjgl.system.MemoryStack")
            val pbClass = Class.forName("org.lwjgl.PointerBuffer")
            val tinyClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
            val stack = stackClass.getMethod("stackPush").invoke(null)
            try {
                val target = findOpenFileDialog(tinyClass, pbClass) ?: return PickResult.unavailable()
                val allowMulti = allowMultiValue(target.parameterTypes[4])
                val result = target.invoke(
                    null,
                    title ?: "",
                    defaultPath ?: "",
                    null,
                    null,
                    allowMulti
                )
                if (result is String && result.isNotBlank()) {
                    PickResult.picked(Path.of(result))
                } else {
                    PickResult.canceled()
                }
            } finally {
                closeStack(stackClass, stack)
            }
        } catch (_: Throwable) {
            PickResult.unavailable()
        }
    }

    private fun tryTinySaveDialog(title: String?, suggestedName: String?): PickResult {
        return try {
            val defaultPath = suggestedName ?: ""
            val stackClass = Class.forName("org.lwjgl.system.MemoryStack")
            val pbClass = Class.forName("org.lwjgl.PointerBuffer")
            val tinyClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
            val stack = stackClass.getMethod("stackPush").invoke(null)
            try {
                val target = findSaveFileDialog(tinyClass, pbClass) ?: return PickResult.unavailable()
                val result = target.invoke(
                    null,
                    title ?: "",
                    defaultPath,
                    null,
                    null
                )
                if (result is String && result.isNotBlank()) {
                    PickResult.picked(Path.of(result))
                } else {
                    PickResult.canceled()
                }
            } finally {
                closeStack(stackClass, stack)
            }
        } catch (_: Throwable) {
            PickResult.unavailable()
        }
    }

    private fun tryTinyFolderDialog(title: String?): PickResult {
        return try {
            val tinyClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
            var target: Method? = null
            for (method in tinyClass.methods) {
                if (method.name != "tinyfd_selectFolderDialog") {
                    continue
                }
                val params = method.parameterTypes
                if (params.size != 2) {
                    continue
                }
                if (CharSequence::class.java.isAssignableFrom(params[0]) && CharSequence::class.java.isAssignableFrom(params[1])) {
                    target = method
                    break
                }
            }
            if (target == null) {
                return PickResult.unavailable()
            }
            val result = target.invoke(null, title ?: "", "")
            if (result is String && result.isNotBlank()) {
                PickResult.picked(Path.of(result))
            } else {
                PickResult.canceled()
            }
        } catch (_: Throwable) {
            PickResult.unavailable()
        }
    }

    private fun tryAppleScriptFileDialog(title: String?): PickResult {
        return try {
            val updateDir = DlcUpdateService.updateDir
            val defaultLocation = updateDir?.toString() ?: ""
            val safeTitle = escapeAppleScript(title ?: "Import Update")
            val safeDefault = escapeAppleScript(defaultLocation)
            val script = if (safeDefault.isBlank()) {
                "POSIX path of (choose file of type {\"zip\"} with prompt \"$safeTitle\")"
            } else {
                "POSIX path of (choose file of type {\"zip\"} with prompt \"$safeTitle\" default location POSIX file \"$safeDefault\")"
            }
            val process = ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val line = reader.readLine()
                val exit = process.waitFor()
                if (exit == 0 && !line.isNullOrBlank()) {
                    return PickResult.picked(Path.of(line.trim()))
                }
                PickResult.canceled()
            }
        } catch (_: Throwable) {
            PickResult.unavailable()
        }
    }

    private fun tryAppleScriptFileDialogAny(title: String?, defaultDir: Path?): PickResult {
        return try {
            val defaultLocation = defaultDir?.toString() ?: ""
            val safeTitle = escapeAppleScript(title ?: "Select File")
            val safeDefault = escapeAppleScript(defaultLocation)
            val script = if (safeDefault.isBlank()) {
                "POSIX path of (choose file with prompt \"$safeTitle\")"
            } else {
                "POSIX path of (choose file with prompt \"$safeTitle\" default location POSIX file \"$safeDefault\")"
            }
            val process = ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val line = reader.readLine()
                val exit = process.waitFor()
                if (exit == 0 && !line.isNullOrBlank()) {
                    return PickResult.picked(Path.of(line.trim()))
                }
                PickResult.canceled()
            }
        } catch (_: Throwable) {
            PickResult.unavailable()
        }
    }

    private fun tryAppleScriptSaveDialog(title: String?, suggestedName: String?): PickResult {
        return try {
            val safeTitle = escapeAppleScript(title ?: "Save File")
            val safeName = escapeAppleScript(suggestedName ?: "")
            val script = if (safeName.isBlank()) {
                "POSIX path of (choose file name with prompt \"$safeTitle\")"
            } else {
                "POSIX path of (choose file name with prompt \"$safeTitle\" default name \"$safeName\")"
            }
            val process = ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val line = reader.readLine()
                if (!line.isNullOrBlank()) {
                    return PickResult.picked(Path.of(line.trim()))
                }
            }
            PickResult.canceled()
        } catch (_: Throwable) {
            PickResult.unavailable()
        }
    }

    private fun tryAppleScriptFolderDialog(title: String?): PickResult {
        return try {
            val safeTitle = escapeAppleScript(title ?: "Select Folder")
            val script = "POSIX path of (choose folder with prompt \"$safeTitle\")"
            val process = ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val line = reader.readLine()
                val exit = process.waitFor()
                if (exit == 0 && !line.isNullOrBlank()) {
                    return PickResult.picked(Path.of(line.trim()))
                }
                PickResult.canceled()
            }
        } catch (_: Throwable) {
            PickResult.unavailable()
        }
    }

    private fun findOpenFileDialog(tinyClass: Class<*>, pbClass: Class<*>): Method? {
        for (method in tinyClass.methods) {
            if (method.name != "tinyfd_openFileDialog") {
                continue
            }
            val params = method.parameterTypes
            if (params.size != 5) {
                continue
            }
            if (CharSequence::class.java.isAssignableFrom(params[0]) &&
                CharSequence::class.java.isAssignableFrom(params[1]) &&
                params[2].isAssignableFrom(pbClass) &&
                CharSequence::class.java.isAssignableFrom(params[3])
            ) {
                return method
            }
        }
        return null
    }

    private fun findSaveFileDialog(tinyClass: Class<*>, pbClass: Class<*>): Method? {
        for (method in tinyClass.methods) {
            if (method.name != "tinyfd_saveFileDialog") {
                continue
            }
            val params = method.parameterTypes
            if (params.size != 4) {
                continue
            }
            if (CharSequence::class.java.isAssignableFrom(params[0]) &&
                CharSequence::class.java.isAssignableFrom(params[1]) &&
                params[2].isAssignableFrom(pbClass) &&
                CharSequence::class.java.isAssignableFrom(params[3])
            ) {
                return method
            }
        }
        return null
    }

    private fun allowMultiValue(allowType: Class<*>): Any {
        return if (allowType == Boolean::class.javaPrimitiveType || allowType == Boolean::class.java) {
            false
        } else {
            0
        }
    }

    private fun closeStack(stackClass: Class<*>, stack: Any?) {
        if (stack is AutoCloseable) {
            stack.close()
        } else if (stack != null) {
            stackClass.getMethod("close").invoke(stack)
        }
    }

    private fun escapeAppleScript(value: String?): String {
        if (value == null) {
            return ""
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun openUpdateDir() {
        val folder = DlcUpdateService.updateDir
        try {
            if (folder == null) {
                return
            }
            Files.createDirectories(folder)
            val osName = System.getProperty("os.name", "").lowercase()
            if (osName.contains("mac")) {
                ProcessBuilder("/usr/bin/open", folder.toString()).start()
                return
            }
            if (osName.contains("win")) {
                ProcessBuilder("explorer", folder.toString()).start()
                return
            }
            ProcessBuilder("xdg-open", folder.toString()).start()
        } catch (_: Throwable) {
        }
    }

    private class PickResult private constructor(
        val path: Path?,
        val canceled: Boolean,
        val available: Boolean,
    ) {
        companion object {
            fun picked(path: Path): PickResult {
                return PickResult(path, false, true)
            }

            fun canceled(): PickResult {
                return PickResult(null, true, true)
            }

            fun unavailable(): PickResult {
                return PickResult(null, false, false)
            }
        }
    }
}
