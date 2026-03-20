package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.core.DlcType;

public class DlcServerListRow {
    public enum RowType {
        GROUP_HEADER,
        PATH_HEADER,
        ITEM
    }

    private final RowType type;
    private final String label;
    private final DlcType groupType;
    private final DlcServerItem item;
    private final java.util.List<DlcServerItem> items;
    private final boolean rootPath;
    private final boolean hideTopSeparator;

    private DlcServerListRow(RowType type, String label, DlcType groupType, DlcServerItem item,
                             java.util.List<DlcServerItem> items, boolean rootPath, boolean hideTopSeparator) {
        this.type = type;
        this.label = label;
        this.groupType = groupType;
        this.item = item;
        this.items = items;
        this.rootPath = rootPath;
        this.hideTopSeparator = hideTopSeparator;
    }

    public static DlcServerListRow groupHeader(DlcType type) {
        return new DlcServerListRow(RowType.GROUP_HEADER, null, type, null, null, false, false);
    }

    public static DlcServerListRow pathHeader(String label, java.util.List<DlcServerItem> items, boolean rootPath, boolean hideTopSeparator) {
        return new DlcServerListRow(RowType.PATH_HEADER, label, null, null, items, rootPath, hideTopSeparator);
    }

    public static DlcServerListRow item(DlcServerItem item) {
        return new DlcServerListRow(RowType.ITEM, null, null, item, null, false, false);
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

    public DlcServerItem getItem() {
        return item;
    }

    public java.util.List<DlcServerItem> getItems() {
        return items;
    }

    public boolean isRootPath() {
        return rootPath;
    }

    public boolean shouldHideTopSeparator() {
        return hideTopSeparator;
    }
}
