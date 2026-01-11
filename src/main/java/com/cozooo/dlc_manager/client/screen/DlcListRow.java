package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcType;

public class DlcListRow {
    public enum RowType {
        GROUP_HEADER,
        PATH_HEADER,
        ITEM
    }

    private final RowType type;
    private final String label;
    private final DlcType groupType;
    private final DlcItem item;

    private DlcListRow(RowType type, String label, DlcType groupType, DlcItem item) {
        this.type = type;
        this.label = label;
        this.groupType = groupType;
        this.item = item;
    }

    public static DlcListRow groupHeader(DlcType type) {
        return new DlcListRow(RowType.GROUP_HEADER, null, type, null);
    }

    public static DlcListRow pathHeader(String label) {
        return new DlcListRow(RowType.PATH_HEADER, label, null, null);
    }

    public static DlcListRow item(DlcItem item) {
        return new DlcListRow(RowType.ITEM, null, null, item);
    }

    public RowType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public DlcType getGroupType() {
        return groupType;
    }

    public DlcItem getItem() {
        return item;
    }
}
