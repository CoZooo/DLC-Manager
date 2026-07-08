package com.cozooo.dlc_manager.client.required

object DlcRequiredRuntimeState {
    private val baseline = HashMap<String, Boolean>()
    private val override = HashMap<String, Boolean>()

    @JvmStatic
    fun getBaseline(identifier: String?, current: Boolean): Boolean {
        if (identifier.isNullOrBlank()) {
            return current
        }
        baseline.putIfAbsent(identifier, current)
        return baseline.getOrDefault(identifier, current)
    }

    @JvmStatic
    fun getOverride(identifier: String?): Boolean? {
        if (identifier.isNullOrBlank()) {
            return null
        }
        return override[identifier]
    }

    @JvmStatic
    fun setOverride(identifier: String?, value: Boolean?) {
        if (identifier.isNullOrBlank()) {
            return
        }
        if (value == null) {
            override.remove(identifier)
        } else {
            override[identifier] = value
        }
    }

    @JvmStatic
    fun isPending(identifier: String?, baseline: Boolean): Boolean {
        val override = getOverride(identifier)
        return override != null && override != baseline
    }

    @JvmStatic
    fun getDesired(identifier: String?, current: Boolean): Boolean {
        val override = getOverride(identifier)
        return override ?: current
    }

    @JvmStatic
    fun hasPendingChanges(): Boolean {
        for ((id, override) in override) {
            val base = baseline[id]
            if (base == null || override != base) {
                return true
            }
        }
        return false
    }
}
