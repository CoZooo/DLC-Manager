package com.cozooo.dlc_manager.client.core;

import com.cozooo.dlc_manager.platform.DlcTextCompat;

import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import com.cozooo.dlc_manager.client.update.DlcUpdateService.DlcUpdateMessage;
import net.minecraft.network.chat.Component;

public final class DlcMessageAdapter {
    private DlcMessageAdapter() {
    }

    public static Component toComponent(DlcUpdateMessage message) {
        if (message == null) {
            return DlcTextCompat.empty();
        }
        return switch (message.type()) {
            case EMPTY -> DlcTextCompat.empty();
            case LITERAL -> DlcTextCompat.literal(message.value());
            case TRANSLATABLE -> DlcTextCompat.translatable(message.value(), convertArgs(message.args()));
        };
    }

    public static Component toFailureComponent(DlcUpdateService.UpdateApplyResult result) {
        return toComponent(DlcUpdateService.toFailureUpdateMessage(result));
    }

    public static Component toImportValidationComponent(DlcUpdateService.ImportValidationResult result) {
        return toComponent(DlcUpdateService.toImportValidationUpdateMessage(result));
    }

    public static Component toCheckFailureComponent(String error) {
        return toComponent(DlcPackReleaseUpdateService.toCheckFailureUpdateMessage(error));
    }

    public static Component toDownloadFailureComponent(String error) {
        return toComponent(DlcPackReleaseUpdateService.toDownloadFailureUpdateMessage(error));
    }

    private static Object[] convertArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            converted[i] = arg instanceof DlcUpdateMessage message ? toComponent(message) : arg;
        }
        return converted;
    }
}
