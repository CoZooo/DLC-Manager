package com.cozooo.dlc_manager.client.config

import com.cozooo.dlc_manager.platform.DlcTextCompat

import net.minecraft.network.chat.Component

abstract class DlcConfigValue(
    val name: String,
    private val commentKey: String?,
    private val translationKey: String,
) {
    val configGuiDisplayName: String
        get() = DlcTextCompat.translatable(translationKey).string
    val comment: String
        get() = if (commentKey.isNullOrBlank()) "" else DlcTextCompat.translatable(commentKey).string

    abstract val stringValue: String
    abstract val defaultStringValue: String
    abstract fun setValueFromString(value: String?)

    fun resetToDefault() { setValueFromString(defaultStringValue) }

    class StringValue(name: String, defaultValue: String?, commentKey: String?, translationKey: String) : DlcConfigValue(name, commentKey, translationKey) {
        override val defaultStringValue = defaultValue.orEmpty()
        override var stringValue = defaultStringValue
        override fun setValueFromString(value: String?) { stringValue = value.orEmpty() }
    }

    class BooleanValue(name: String, defaultValue: Boolean, commentKey: String?, translationKey: String) : DlcConfigValue(name, commentKey, translationKey) {
        override val defaultStringValue = defaultValue.toString()
        var booleanValue = defaultValue
        override val stringValue: String
            get() = booleanValue.toString()
        override fun setValueFromString(value: String?) {
            when {
                value?.trim().equals("true", ignoreCase = true) -> booleanValue = true
                value?.trim().equals("false", ignoreCase = true) -> booleanValue = false
            }
        }
    }

    class IntegerValue(name: String, defaultValue: Int, val minIntegerValue: Int, val maxIntegerValue: Int, commentKey: String?, translationKey: String) : DlcConfigValue(name, commentKey, translationKey) {
        init { require(minIntegerValue <= maxIntegerValue) { "Invalid integer range" } }
        private val boundedDefault = defaultValue.coerceIn(minIntegerValue, maxIntegerValue)
        override val defaultStringValue = boundedDefault.toString()
        var integerValue = boundedDefault
            set(value) { field = value.coerceIn(minIntegerValue, maxIntegerValue) }
        override val stringValue: String
            get() = integerValue.toString()
        override fun setValueFromString(value: String?) {
            value?.trim()?.toIntOrNull()?.let { integerValue = it }
        }
    }
}
