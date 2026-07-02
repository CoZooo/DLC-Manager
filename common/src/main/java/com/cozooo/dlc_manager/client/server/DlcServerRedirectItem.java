package com.cozooo.dlc_manager.client.server;

import java.util.Collections;
import java.util.Map;

public class DlcServerRedirectItem {
    public String redirectUrl;
    public String fallbackName;
    public Map<String, String> displayNames = Collections.emptyMap();
    public Map<String, String> notices = Collections.emptyMap();
    public Map<String, String> tips = Collections.emptyMap();
}
