package com.cozooo.dlc_manager.client.pack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class DlcPackManifest private constructor(
    @JvmField val files: List<FileRef>,
) {
    class FileRef(
        @JvmField val projectId: String,
        @JvmField val fileId: String,
    )

    companion object {
        @JvmStatic
        fun empty(): DlcPackManifest {
            return DlcPackManifest(emptyList())
        }

        @JvmStatic
        fun read(path: Path?): DlcPackManifest {
            if (path == null || !Files.isRegularFile(path)) {
                return empty()
            }
            return try {
                Files.newBufferedReader(path).use { reader ->
                    val parsed = JsonParser.parseReader(reader)
                    if (parsed == null || !parsed.isJsonObject) {
                        empty()
                    } else {
                        read(parsed.asJsonObject)
                    }
                }
            } catch (_: IOException) {
                empty()
            } catch (_: RuntimeException) {
                empty()
            }
        }

        @JvmStatic
        fun read(obj: JsonObject?): DlcPackManifest {
            if (obj == null || !obj.has("files") || !obj.get("files").isJsonArray) {
                return empty()
            }
            val out = ArrayList<FileRef>()
            for (element in obj.getAsJsonArray("files")) {
                if (element == null || !element.isJsonObject) {
                    continue
                }
                val file = element.asJsonObject
                val projectId = readId(file, "projectID")
                val fileId = readId(file, "fileID")
                if (projectId != null && fileId != null) {
                    out.add(FileRef(projectId, fileId))
                }
            }
            return DlcPackManifest(out)
        }

        private fun readId(obj: JsonObject?, key: String): String? {
            if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
                return null
            }
            return try {
                obj.get(key).asString?.trim()?.takeIf { it.isNotBlank() }
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
