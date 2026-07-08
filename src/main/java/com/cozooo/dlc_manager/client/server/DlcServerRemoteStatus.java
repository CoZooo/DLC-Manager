package com.cozooo.dlc_manager.client.server;

import java.util.ArrayList;
import java.util.List;

public class DlcServerRemoteStatus {
    public boolean running;
    public boolean finished;
    public boolean success;
    public int totalCount;
    public int completedCount;
    public long totalBytes;
    public long downloadedBytes;
    public String statusKey;
    public List<DlcServerRemoteItem> items = new ArrayList<>();
    public String error;
}
