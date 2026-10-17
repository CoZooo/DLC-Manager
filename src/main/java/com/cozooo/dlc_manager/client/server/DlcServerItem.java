package com.cozooo.dlc_manager.client.server;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DlcServerItem {
    public String id;
    public String name;
    public String identifier;
    public String path;
    public String type;
    public boolean enabled;
    public Boolean baselineEnabled;
    public Map<String, String> displayNames = Collections.emptyMap();
    public Map<String, String> tips = Collections.emptyMap();
    public Map<String, String> alarmTips = Collections.emptyMap();
    public List<String> dependencies = List.of();
    public List<String> conflicts = List.of();
    public boolean allowEnableByAll = true;
    public boolean alarm;
    public boolean hidden;
    public boolean configType;
    public String appliedTargetRaw;
    public String redirectUrl;
    public boolean presetBlocked;
    public List<String> presetBlockers = List.of();
}
