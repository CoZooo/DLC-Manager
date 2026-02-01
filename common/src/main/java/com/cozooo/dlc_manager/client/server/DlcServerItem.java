package com.cozooo.dlc_manager.client.server;

import java.util.List;
import java.util.Map;

public class DlcServerItem {
    public String id;
    public String name;
    public String identifier;
    public String path;
    public String type;
    public boolean enabled;
    public Map<String, String> displayNames;
    public Map<String, String> tips;
    public List<String> dependencies;
    public List<String> conflicts;
    public boolean allowEnableByAll;
}
