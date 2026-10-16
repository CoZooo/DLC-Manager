package com.cozooo.dlc_manager.platform

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object DlcTextCompat {
    @JvmStatic
    fun literal(value: String): MutableComponent {
        //? if >=1.19.2 {
        return Component.literal(value)
        //?} else {
        //return construct("net.minecraft.network.chat.TextComponent", arrayOf(String::class.java), value)
        //?}
    }

    @JvmStatic
    fun translatable(key: String, vararg args: Any?): MutableComponent {
        //? if >=1.19.2 {
        return Component.translatable(key, *args.map { it ?: "" }.toTypedArray())
        //?} else {
        //return construct("net.minecraft.network.chat.TranslatableComponent", arrayOf(String::class.java, Array<Any>::class.java), key, args)
        //?}
    }

    @JvmStatic
    fun empty(): MutableComponent = literal("")

    private fun construct(name: String, types: Array<Class<*>>, vararg args: Any?): MutableComponent {
        return try {
            Class.forName(name).getConstructor(*types).newInstance(*args) as MutableComponent
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException(e)
        }
    }
}
