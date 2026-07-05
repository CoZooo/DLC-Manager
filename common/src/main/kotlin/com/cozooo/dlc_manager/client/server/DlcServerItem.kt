package com.cozooo.dlc_manager.client.server

class DlcServerItem {
    @JvmField
    var id: String? = null

    @JvmField
    var name: String? = null

    @JvmField
    var identifier: String? = null

    @JvmField
    var path: String? = null

    @JvmField
    var type: String? = null

    @JvmField
    var enabled: Boolean = false

    @JvmField
    var baselineEnabled: Boolean? = null

    @JvmField
    var displayNames: Map<String, String> = emptyMap()

    @JvmField
    var tips: Map<String, String> = emptyMap()

    @JvmField
    var dependencies: List<String> = emptyList()

    @JvmField
    var conflicts: List<String> = emptyList()

    @JvmField
    var allowEnableByAll: Boolean = true

    @JvmField
    var alarm: Boolean = false

    @JvmField
    var hidden: Boolean = false

    @JvmField
    var configType: Boolean = false

    @JvmField
    var appliedTargetRaw: String? = null

    @JvmField
    var redirectUrl: String? = null

    @JvmField
    var presetBlocked: Boolean = false

    @JvmField
    var presetBlockers: List<String> = emptyList()
}
