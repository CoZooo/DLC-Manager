package com.cozooo.dlc_manager.client.server

class DlcServerPackInfo {
    @JvmField var archivePath: String? = null
    @JvmField var orderKey: String? = null
    @JvmField var fileName: String? = null
    @JvmField var identifier: String? = null
    @JvmField var version: String? = null
    @JvmField var displayName: String? = null
    @JvmField var description: String? = null
    @JvmField var authors: MutableList<String>? = null
    @JvmField var links: MutableMap<String, String>? = null
    @JvmField var iconBase64: String? = null
    @JvmField var format: String? = null
    @JvmField var installed: Boolean = false
    @JvmField var installedVersion: String? = null
}
