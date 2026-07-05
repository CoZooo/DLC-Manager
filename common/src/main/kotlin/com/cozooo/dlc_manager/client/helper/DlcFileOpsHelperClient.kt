package com.cozooo.dlc_manager.client.helper

import com.cozooo.dlc_fileops_helper.FileOpsHelperBootstrap
import com.cozooo.dlc_fileops_helper.api.FileOpsHelperApi
import com.cozooo.dlc_manager.core.DlcPaths
import java.nio.file.Path
import java.util.Locale

object DlcFileOpsHelperClient {
    private val initLock = Any()
    @Volatile
    private var initialized = false
    @Volatile
    private var initializedGameDir: Path? = null
    @Volatile
    private var initializedTmpRoot: Path? = null

    enum class HelperState {
        NOT_REQUIRED,
        READY,
        UNSUPPORTED
    }

    class FileOpResult(
        val ok: Boolean,
        val helperScheduled: Boolean,
        val message: String,
    ) {
        fun ok(): Boolean = ok

        fun helperScheduled(): Boolean = helperScheduled

        fun message(): String = message

        companion object {
            @JvmStatic
            fun success(helperScheduled: Boolean): FileOpResult {
                return FileOpResult(true, helperScheduled, "")
            }

            @JvmStatic
            fun failure(message: String?): FileOpResult {
                return FileOpResult(false, false, message ?: "")
            }
        }
    }

    @JvmStatic
    fun shouldUseHelper(): Boolean {
        return isWindows()
    }

    @JvmStatic
    fun detectState(): HelperState {
        if (!shouldUseHelper()) {
            return HelperState.NOT_REQUIRED
        }
        return if (ensureInitialized() && isApiSupported()) HelperState.READY else HelperState.UNSUPPORTED
    }

    @JvmStatic
    fun scheduleDelete(path: Path?): FileOpResult {
        if (path == null) {
            return FileOpResult.failure("Failed to delete: missing target path")
        }
        return invokeSchedule("scheduleDelete", arrayOf(List::class.java), listOf(path.toString()), "delete")
    }

    @JvmStatic
    fun scheduleCopy(source: Path?, target: Path?, replaceExisting: Boolean): FileOpResult {
        if (source == null || target == null) {
            return FileOpResult.failure("Failed to copy: missing source or target path")
        }
        return invokeSchedule(
            "scheduleCopy",
            arrayOf(String::class.java, String::class.java, Boolean::class.javaPrimitiveType!!),
            source.toString(),
            target.toString(),
            replaceExisting,
            "copy"
        )
    }

    @JvmStatic
    fun scheduleMove(source: Path?, target: Path?, replaceExisting: Boolean): FileOpResult {
        if (source == null || target == null) {
            return FileOpResult.failure("Failed to move: missing source or target path")
        }
        return invokeSchedule(
            "scheduleMove",
            arrayOf(String::class.java, String::class.java, Boolean::class.javaPrimitiveType!!),
            source.toString(),
            target.toString(),
            replaceExisting,
            "move"
        )
    }

    @JvmStatic
    fun scheduleWriteText(target: Path?, content: String?): FileOpResult {
        if (target == null) {
            return FileOpResult.failure("Failed to write: missing target path")
        }
        return invokeSchedule(
            "scheduleWriteText",
            arrayOf(String::class.java, String::class.java),
            target.toString(),
            content ?: "",
            "write"
        )
    }

    @JvmStatic
    fun cancelPendingDelete(target: Path?): Boolean {
        return cancelPendingOperation(target, "delete")
    }

    @JvmStatic
    fun cancelPendingOperation(target: Path?, requiredType: String?): Boolean {
        if (target == null) {
            return false
        }
        val state = detectState()
        if (state != HelperState.READY) {
            return false
        }
        val normalizedTarget = normalizePath(target)
        if (normalizedTarget.isBlank()) {
            return false
        }
        return try {
            val rawPending = FileOpsHelperApi.getPendingOperations()
            if (rawPending !is Map<*, *> || !asBoolean(rawPending["ok"])) {
                return false
            }
            if (!hasPendingOperation(rawPending["operations"], normalizedTarget, requiredType)) {
                return false
            }
            val rawCancel = FileOpsHelperApi.cancelPendingOperationsByTarget(listOf(normalizedTarget))
            if (rawCancel !is Map<*, *> || !asBoolean(rawCancel["ok"])) {
                return false
            }
            val removedCount = rawCancel["removedCount"]
            if (removedCount is Number) {
                removedCount.toInt() > 0
            } else {
                asInt(removedCount) > 0
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun invokeSchedule(methodName: String, parameterTypes: Array<Class<*>>, args: Array<Any?>, operation: String): FileOpResult {
        val state = detectState()
        if (state == HelperState.NOT_REQUIRED) {
            return FileOpResult.failure("Failed to $operation: helper is not enabled on this platform")
        }
        if (state == HelperState.UNSUPPORTED) {
            return FileOpResult.failure("Failed to $operation: FileOps Helper is not supported on this platform.")
        }
        return try {
            val raw = when (methodName) {
                "scheduleDelete" -> FileOpsHelperApi.scheduleDelete(asStringList(args[0]))
                "scheduleCopy" -> FileOpsHelperApi.scheduleCopy(args[0] as String, args[1] as String, args[2] as Boolean)
                "scheduleMove" -> FileOpsHelperApi.scheduleMove(args[0] as String, args[1] as String, args[2] as Boolean)
                "scheduleWriteText" -> FileOpsHelperApi.scheduleWriteText(args[0] as String, args[1] as String)
                else -> null
            }
            if (raw !is Map<*, *>) {
                return FileOpResult.failure("Failed to $operation: invalid helper response")
            }
            val ok = asBoolean(raw["ok"])
            var message = asString(raw["message"])
            if (ok) {
                return FileOpResult.success(true)
            }
            if (message.isBlank()) {
                message = "Failed to $operation: helper scheduling failed"
            } else if (!message.startsWith("Failed to ")) {
                message = "Failed to $operation: $message"
            }
            FileOpResult.failure(message)
        } catch (throwable: Throwable) {
            var message = throwable.message
            if (message.isNullOrBlank()) {
                message = "helper API unavailable"
            }
            FileOpResult.failure("Failed to $operation: $message")
        }
    }

    private fun invokeSchedule(methodName: String, parameterTypes: Array<Class<*>>, arg1: Any?, operation: String): FileOpResult {
        return invokeSchedule(methodName, parameterTypes, arrayOf(arg1), operation)
    }

    private fun invokeSchedule(
        methodName: String,
        parameterTypes: Array<Class<*>>,
        arg1: Any?,
        arg2: Any?,
        arg3: Boolean,
        operation: String,
    ): FileOpResult {
        return invokeSchedule(methodName, parameterTypes, arrayOf(arg1, arg2, arg3), operation)
    }

    private fun invokeSchedule(methodName: String, parameterTypes: Array<Class<*>>, arg1: Any?, arg2: Any?, operation: String): FileOpResult {
        return invokeSchedule(methodName, parameterTypes, arrayOf(arg1, arg2), operation)
    }

    private fun isApiSupported(): Boolean {
        if (!ensureInitialized()) {
            return false
        }
        return try {
            FileOpsHelperApi.isSupported()
        } catch (_: Throwable) {
            false
        }
    }

    private fun ensureInitialized(): Boolean {
        val gameDir: Path
        val tmpRoot: Path
        try {
            gameDir = DlcPaths.gameDir()
            tmpRoot = DlcPaths.tmpRoot()
        } catch (_: Throwable) {
            return false
        }
        if (initialized && samePath(initializedGameDir, gameDir) && samePath(initializedTmpRoot, tmpRoot)) {
            return true
        }
        synchronized(initLock) {
            if (initialized && samePath(initializedGameDir, gameDir) && samePath(initializedTmpRoot, tmpRoot)) {
                return true
            }
            return try {
                FileOpsHelperBootstrap.initialize(gameDir, tmpRoot)
                initialized = true
                initializedGameDir = gameDir
                initializedTmpRoot = tmpRoot
                true
            } catch (_: Throwable) {
                initialized = false
                initializedGameDir = gameDir
                initializedTmpRoot = tmpRoot
                false
            }
        }
    }

    private fun samePath(first: Path?, second: Path?): Boolean {
        if (first === second) {
            return true
        }
        if (first == null || second == null) {
            return false
        }
        return try {
            first.toAbsolutePath().normalize() == second.toAbsolutePath().normalize()
        } catch (_: Throwable) {
            first == second
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("win")
    }

    private fun safeTrim(value: String?): String {
        return value?.trim() ?: ""
    }

    private fun hasPendingOperation(operationsRaw: Any?, normalizedTarget: String, requiredType: String?): Boolean {
        if (operationsRaw !is Iterable<*>) {
            return false
        }
        val normalizedType = safeTrim(requiredType).lowercase(Locale.ROOT)
        for (entry in operationsRaw) {
            if (entry !is Map<*, *>) {
                continue
            }
            val type = safeTrim(asString(entry["type"])).lowercase(Locale.ROOT)
            if (normalizedType.isNotBlank() && normalizedType != type) {
                continue
            }
            val path = normalizePath(asString(entry["path"]))
            val source = normalizePath(asString(entry["source"]))
            val target = normalizePath(asString(entry["target"]))
            if (normalizedTarget == path || normalizedTarget == source || normalizedTarget == target) {
                return true
            }
        }
        return false
    }

    private fun normalizePath(path: Path?): String {
        if (path == null) {
            return ""
        }
        return try {
            path.toAbsolutePath().normalize().toString()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun normalizePath(path: String?): String {
        if (path.isNullOrBlank()) {
            return ""
        }
        return try {
            var normalized = Path.of(path)
            if (!normalized.isAbsolute) {
                normalized = DlcPaths.gameDir().resolve(normalized)
            }
            normalized.toAbsolutePath().normalize().toString()
        } catch (_: Throwable) {
            safeTrim(path)
        }
    }

    private fun asString(value: Any?): String {
        if (value == null) {
            return ""
        }
        return value.toString()
    }

    private fun asStringList(value: Any?): List<String> {
        if (value !is Iterable<*>) {
            return emptyList()
        }
        val result = ArrayList<String>()
        for (item in value) {
            result.add(asString(item))
        }
        return result
    }

    private fun asBoolean(value: Any?): Boolean {
        if (value is Boolean) {
            return value
        }
        if (value == null) {
            return false
        }
        return value.toString().toBoolean()
    }

    private fun asInt(value: Any?): Int {
        if (value is Number) {
            return value.toInt()
        }
        if (value == null) {
            return 0
        }
        return try {
            value.toString().toInt()
        } catch (_: NumberFormatException) {
            0
        }
    }
}
