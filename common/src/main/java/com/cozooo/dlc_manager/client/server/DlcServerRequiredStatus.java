package com.cozooo.dlc_manager.client.server;

import java.util.ArrayList;
import java.util.List;

public class DlcServerRequiredStatus {
    public boolean running;
    public boolean finished;
    public boolean success;
    public int totalCount;
    public int completedCount;
    public long totalBytes;
    public long downloadedBytes;
    public List<DlcServerRequiredProgressItem> items = new ArrayList<>();
    public String error;
}
