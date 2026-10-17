package com.cozooo.dlc_manager.client.server;

public class DlcServerEntry {
    public String name;
    public String host;
    public int port;
    public String password;
    public boolean useTcp = true;
    public boolean usePortMultiplexer;
    public int rconPort = 25575;
    public String rconPassword = "";

    public String getDisplayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        String hostValue = host == null ? "" : host;
        return hostValue + ":" + port;
    }

    public String getKey() {
        String hostValue = host == null ? "" : host;
        return hostValue + ":" + port;
    }
}
