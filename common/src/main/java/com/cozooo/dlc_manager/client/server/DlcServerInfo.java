package com.cozooo.dlc_manager.client.server;

import java.util.List;

public class DlcServerInfo {
    public String baseVersion;
    public String dlcVersion;
    public List<DlcServerItem> items;
    public int remoteMissing;
    public int requiredMissing;
}
