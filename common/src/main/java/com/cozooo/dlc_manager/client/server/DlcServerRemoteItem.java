package com.cozooo.dlc_manager.client.server;

public class DlcServerRemoteItem {
    public String key;
    public String name;
    public java.util.Map<String, String> displayNames = java.util.Collections.emptyMap();
    public long totalBytes;
    public long downloadedBytes;
    public boolean done;
    public boolean success;
    public String error;
}
