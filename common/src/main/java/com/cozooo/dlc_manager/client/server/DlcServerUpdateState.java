package com.cozooo.dlc_manager.client.server;

import com.cozooo.dlc_manager.client.update.DlcUpdateMeta;
import java.util.List;

public class DlcServerUpdateState {
    public String baseVersion;
    public String dlcVersion;
    public String rollbackFrom;
    public List<DlcUpdateMeta> history;
    public List<DlcServerUpdateInfo> updates;
}
