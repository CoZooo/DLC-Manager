package com.cozooo.dlc_manager.client.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DlcServerRequiredItem {
    public String id;
    public String identifier;
    public String name;
    public Map<String, String> displayNames = new HashMap<>();
    public Map<String, String> tips = new HashMap<>();
    public List<String> dependencies = new ArrayList<>();
    public boolean hidden;
    public boolean userRemoved;
    public boolean hasFile;
    public boolean needsApply;
    public boolean applied;
    public String fileName;
}
