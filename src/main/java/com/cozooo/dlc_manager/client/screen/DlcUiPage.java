package com.cozooo.dlc_manager.client.screen;

public enum DlcUiPage {
    PAGE("page", "page"),
    MANAGER("manager", "manager"),
    SERVER_MANAGER("server_manager", "manager"),
    LOCAL_CONFIG("local_config", "page"),
    SERVER_CONFIG("server_config", "page"),
    LOCAL_PRESETS("local_presets", "page"),
    SERVER_PRESETS("server_presets", "page"),
    LOCAL_PACKS("local_packs", "page"),
    SERVER_PACKS("server_packs", "page"),
    LOCAL_PACK_URL("local_pack_url", "page"),
    SERVER_PACK_URL("server_pack_url", "page"),
    SERVER_PACK_APPLY("server_pack_apply", "page"),
    LOCAL_UPDATES("local_updates", "page"),
    SERVER_UPDATES("server_updates", "page"),
    LOCAL_UPDATE_URL("local_update_url", "page"),
    SERVER_UPDATE_URL("server_update_url", "page"),
    UPDATE_PROMPT("update_prompt", "page"),
    UPDATE_RESULT("update_result", "page"),
    UPDATE_CHANGELOG("update_changelog", "page"),
    UPDATE_MISSING("update_missing", "page"),
    LOCAL_MODPACK_UPDATE("local_modpack_update", "page"),
    SERVER_MODPACK_UPDATE("server_modpack_update", "page"),
    SELF_UPDATE("self_update", "page"),
    REMOTE_DOWNLOAD("remote_download", "page"),
    REMOTE_PACK_DOWNLOAD("remote_pack_download", "page"),
    SERVER_REMOTE_DOWNLOAD("server_remote_download", "page"),
    REQUIRED_DOWNLOAD("required_download", "page"),
    SERVER_LIST("server_list", "page"),
    SERVER_TOOLS("server_tools", "page"),
    SERVER_EDIT("server_edit", "page"),
    SERVER_PASSWORD("server_password", "page"),
    SERVER_FILES("server_files", "page"),
    SERVER_PUSH("server_push", "page"),
    SERVER_RCON("server_rcon", "page"),
    SERVER_RCON_CONSOLE("server_rcon_console", "page"),
    SERVER_RCON_PLAYERS("server_rcon_players", "page"),
    SERVER_RCON_QUICK("server_rcon_quick", "page"),
    NOTICE("notice", "page"),
    REDIRECT_CONFIRM("redirect_confirm", "page"),
    ALARM_CONFIRM("alarm_confirm", "page"),
    CONFIRM("confirm", "page"),
    SINGLE_BUTTON("single_button", "page");

    private final String id;
    private final String template;

    DlcUiPage(String id, String template) {
        this.id = id;
        this.template = template;
    }

    public String id() { return id; }
    public String template() { return template; }
}
