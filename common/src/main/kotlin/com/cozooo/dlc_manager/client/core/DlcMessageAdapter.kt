package com.cozooo.dlc_manager.client.core

import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.DlcUpdateMessage
import net.minecraft.network.chat.Component

object DlcMessageAdapter {
    @JvmStatic
    fun toComponent(message: DlcUpdateMessage?): Component {
        if (message == null) {
            return Component.empty()
        }
        return when (message.type) {
            DlcUpdateMessage.Type.EMPTY -> Component.empty()
            DlcUpdateMessage.Type.LITERAL -> Component.literal(message.value)
            DlcUpdateMessage.Type.TRANSLATABLE -> Component.translatable(message.value, *convertArgs(message.args()))
        }
    }

    @JvmStatic
    fun toFailureComponent(result: DlcUpdateService.UpdateApplyResult?): Component {
        return toComponent(DlcUpdateService.toFailureUpdateMessage(result))
    }

    @JvmStatic
    fun toImportValidationComponent(result: DlcUpdateService.ImportValidationResult?): Component {
        return toComponent(DlcUpdateService.toImportValidationUpdateMessage(result))
    }

    @JvmStatic
    fun toCheckFailureComponent(error: String?): Component {
        return toComponent(DlcPackReleaseUpdateService.toCheckFailureUpdateMessage(error))
    }

    @JvmStatic
    fun toDownloadFailureComponent(error: String?): Component {
        return toComponent(DlcPackReleaseUpdateService.toDownloadFailureUpdateMessage(error))
    }

    private fun convertArgs(args: Array<out Any?>): Array<Any?> {
        return Array(args.size) { index ->
            val arg = args[index]
            if (arg is DlcUpdateMessage) toComponent(arg) else arg
        }
    }
}
