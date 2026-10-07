package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.ui.config.DlcFxConfigModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DlcFxServerConfigSyncPlanner {
    private DlcFxServerConfigSyncPlanner() {
    }

    public static JsonObject readLocalConfig() {
        DlcFxConfigModel model = new DlcFxConfigModel();
        model.loadFromConfig();
        return model.snapshot();
    }

    public static JsonObject extractServerConfig(JsonObject response) {
        JsonElement element = response == null ? null : response.get("config");
        return element != null && element.isJsonObject() ? element.getAsJsonObject().deepCopy() : null;
    }

    public static Preview preview(JsonObject localConfig, JsonObject serverConfig) {
        if (localConfig == null || serverConfig == null) {
            return new Preview(List.of(), 0, 0, 0);
        }
        DlcFxConfigModel localModel = new DlcFxConfigModel(DlcFxConfigModel.snapshotSource(localConfig));
        DlcFxConfigModel serverModel = new DlcFxConfigModel(DlcFxConfigModel.serverSource(serverConfig, null));
        List<Row> rows = new ArrayList<>();
        int changed = 0;
        int same = 0;
        int masked = 0;
        for (DlcFxConfigModel.Tab tab : DlcFxConfigModel.Tab.values()) {
            for (DlcFxConfigModel.Option localOption : localModel.options(tab)) {
                DlcFxConfigModel.Option serverOption;
                try {
                    serverOption = serverModel.find(localOption.name());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                String localValue = localOption.value();
                String serverValue = serverOption.value();
                boolean secret = isSecret(localOption.name());
                boolean isChanged = !Objects.equals(localValue, serverValue);
                if (isChanged) {
                    changed++;
                } else {
                    same++;
                }
                if (secret) {
                    masked++;
                }
                rows.add(new Row(
                        tab.label(),
                        localOption.label(),
                        localOption.name(),
                        display(localValue, secret),
                        display(serverValue, secret),
                        isChanged,
                        secret));
            }
        }
        rows.sort((left, right) -> {
            if (left.changed() != right.changed()) {
                return left.changed() ? -1 : 1;
            }
            int tabCompare = left.tabLabel().compareToIgnoreCase(right.tabLabel());
            if (tabCompare != 0) {
                return tabCompare;
            }
            return left.label().compareToIgnoreCase(right.label());
        });
        return new Preview(rows, changed, same, masked);
    }

    public static JsonObject buildServerConfigUpdate(JsonObject localConfig, JsonObject serverConfig) {
        JsonObject local = localConfig == null ? new JsonObject() : localConfig.deepCopy();
        JsonObject server = serverConfig == null ? new JsonObject() : serverConfig.deepCopy();
        DlcFxConfigModel localModel = new DlcFxConfigModel(DlcFxConfigModel.snapshotSource(local));
        JsonObject[] updated = new JsonObject[] { server };
        DlcFxConfigModel serverModel = new DlcFxConfigModel(DlcFxConfigModel.serverSource(server, json -> updated[0] = json.deepCopy()));
        for (DlcFxConfigModel.Tab tab : DlcFxConfigModel.Tab.values()) {
            for (DlcFxConfigModel.Option localOption : localModel.options(tab)) {
                DlcFxConfigModel.Option serverOption;
                try {
                    serverOption = serverModel.find(localOption.name());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                serverOption.setValueFromString(localOption.value());
            }
        }
        serverModel.applyToConfig();
        return updated[0];
    }

    private static boolean isSecret(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("key");
    }

    private static String display(String value, boolean secret) {
        if (!secret) {
            return value == null ? "" : value;
        }
        return value == null || value.isBlank() ? "" : "******";
    }

    public record Preview(List<Row> rows, int changedCount, int sameCount, int maskedCount) {
    }

    public record Row(String tabLabel, String label, String optionName, String localValue, String serverValue, boolean changed, boolean masked) {
    }
}
