package com.cozooo.dlc_manager.client.server

class DlcServerRedirectItem {
    @JvmField
    var redirectUrl: String? = null

    @JvmField
    var fallbackName: String? = null

    @JvmField
    var displayNames: Map<String, String> = emptyMap()

    @JvmField
    var notices: Map<String, String> = emptyMap()

    @JvmField
    var tips: Map<String, String> = emptyMap()
}
