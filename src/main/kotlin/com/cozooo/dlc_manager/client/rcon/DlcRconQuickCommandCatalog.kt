package com.cozooo.dlc_manager.client.rcon

object DlcRconQuickCommandCatalog {
    private val commandItems = listOf(
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
        QuickCommandItem.command("dlc_manager.server.rcon_cmd_firetick_off", "gamerule doFireTick false"),
    )

    @JvmStatic
    fun commandItems(): List<QuickCommandItem> {
        return commandItems
    }

    @JvmRecord
    data class QuickCommandItem(
        val separator: Boolean,
        val labelKey: String,
        val command: String,
    ) {
        companion object {
            @JvmStatic
            fun command(labelKey: String, command: String): QuickCommandItem {
                return QuickCommandItem(false, labelKey, command)
            }

            @JvmStatic
            fun separator(labelKey: String): QuickCommandItem {
                return QuickCommandItem(true, labelKey, "")
            }
        }
    }
}
