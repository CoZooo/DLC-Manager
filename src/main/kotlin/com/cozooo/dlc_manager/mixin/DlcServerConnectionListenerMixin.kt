package com.cozooo.dlc_manager.mixin

import com.cozooo.dlc_manager.server.DlcPortMultiplexerHandler
import io.netty.channel.Channel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(targets = ["net.minecraft.server.network.ServerConnectionListener$1"])
abstract class DlcServerConnectionListenerMixin {
    @Inject(method = ["initChannel"], at = [At("TAIL")])
    private fun dlcManagerInstallMultiplexer(channel: Channel, callbackInfo: CallbackInfo) {
        if (channel.pipeline().get("dlc_manager_multiplexer") == null) {
            channel.pipeline().addFirst("dlc_manager_multiplexer", DlcPortMultiplexerHandler())
        }
    }
}
