package com.cozooo.dlc_manager.client.server;

public class DlcServerPackApplyStatus {
    public boolean running;
    public boolean finished;
    public boolean success;
    public boolean canceled;
    public boolean paused;
    public int totalCount;
    public int completedCount;
    public float progress = -1.0F;
    public String message;
    public String error;
}
