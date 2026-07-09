package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcType;

public class DlcListRow {
    public enum RowType {
        GROUP_HEADER,
        PATH_HEADER,
        ITEM,
        REDIRECT_HEADER,
        REDIRECT_ITEM
    }

    private final RowType type;
    private final String label;
    private final DlcType groupType;
    private final DlcItem item;
    private final java.util.List<DlcItem> items;
    private final DlcRedirectEntry redirectEntry;
    private final boolean rootPath;
    private final boolean hideTopSeparator;

    private DlcListRow(RowType type, String label, DlcType groupType, DlcItem item, java.util.List<DlcItem> items,
                       DlcRedirectEntry redirectEntry, boolean rootPath, boolean hideTopSeparator) {
        this.type = type;
        this.label = label;
        this.groupType = groupType;
        this.item = item;
        this.items = items;
        this.redirectEntry = redirectEntry;
        this.rootPath = rootPath;
        this.hideTopSeparator = hideTopSeparator;
    }

    public static DlcListRow groupHeader(DlcType type) {
        return new DlcListRow(RowType.GROUP_HEADER, null, type, null, null, null, false, false);
    }

    public static DlcListRow pathHeader(String label, java.util.List<DlcItem> items, boolean rootPath, boolean hideTopSeparator) {
        return new DlcListRow(RowType.PATH_HEADER, label, null, null, items, null, rootPath, hideTopSeparator);
    }

    public static DlcListRow item(DlcItem item) {
        return new DlcListRow(RowType.ITEM, null, null, item, null, null, false, false);
    }

    public static DlcListRow redirectHeader(String label) {
        return new DlcListRow(RowType.REDIRECT_HEADER, label, null, null, null, null, false, false);
    }

    public static DlcListRow redirectItem(DlcRedirectEntry entry) {
        return new DlcListRow(RowType.REDIRECT_ITEM, null, null, null, null, entry, false, false);
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

    public java.util.List<DlcItem> getItems() {
        return items;
    }

    public DlcRedirectEntry getRedirectEntry() {
        return redirectEntry;
    }

    public boolean isRootPath() {
        return rootPath;
    }

    public boolean shouldHideTopSeparator() {
        return hideTopSeparator;
    }
}
