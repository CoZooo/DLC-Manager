package com.cozooo.dlc_manager.client.required

class DlcRequiredDependency(
    private val identifier: String?,
    private val entry: DlcRequiredEntry?,
) {
    fun getIdentifier(): String? = identifier

    fun getEntry(): DlcRequiredEntry? = entry
}
