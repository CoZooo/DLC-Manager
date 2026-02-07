package com.cozooo.dlc_manager.client.rcon;

import java.util.List;

public final class DlcRconQuickCommandCatalog {
    private static final List<QuickCommandItem> COMMAND_ITEMS = List.of(
        QuickCommandItem.separator("dlc_manager.server.rcon_group_time"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_time_day", "time set day"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_time_night", "time set night"),

        QuickCommandItem.separator("dlc_manager.server.rcon_group_weather"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_weather_cycle_on", "gamerule doWeatherCycle true"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_weather_cycle_off", "gamerule doWeatherCycle false"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_weather_clear", "weather clear"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_weather_rain", "weather rain"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_weather_thunder", "weather thunder"),

        QuickCommandItem.separator("dlc_manager.server.rcon_group_world"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_keep_inventory_on", "gamerule keepInventory true"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_keep_inventory_off", "gamerule keepInventory false"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_daylight_on", "gamerule doDaylightCycle true"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_daylight_off", "gamerule doDaylightCycle false"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_mobspawning_on", "gamerule doMobSpawning true"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_mobspawning_off", "gamerule doMobSpawning false"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_mobgriefing_on", "gamerule mobGriefing true"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_mobgriefing_off", "gamerule mobGriefing false"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_firetick_on", "gamerule doFireTick true"),
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_firetick_off", "gamerule doFireTick false")
    );

    private DlcRconQuickCommandCatalog() {
    }

    public static List<QuickCommandItem> commandItems() {
        return COMMAND_ITEMS;
    }

    public record QuickCommandItem(boolean separator, String labelKey, String command) {
        public static QuickCommandItem command(String labelKey, String command) {
            return new QuickCommandItem(false, labelKey, command);
        }

        public static QuickCommandItem separator(String labelKey) {
            return new QuickCommandItem(true, labelKey, "");
        }
    }
}
