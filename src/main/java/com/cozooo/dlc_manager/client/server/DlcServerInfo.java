package com.cozooo.dlc_manager.client.server;

import java.util.ArrayList;
import java.util.List;

public class DlcServerInfo {
    public String baseVersion;
    public String dlcVersion;
    public String mcVersion;
    public String loaderName;
    public List<DlcServerItem> items;
    public List<DlcServerPresetItem> presets = new ArrayList<>();
    public List<DlcServerRedirectItem> redirects = new ArrayList<>();
    public int remoteMissing;
    public int requiredMissing;
    public boolean hasRequired;
    public boolean hasTaczMod;
    public String modpackUrl;
    public String updateGitRepo;
}
