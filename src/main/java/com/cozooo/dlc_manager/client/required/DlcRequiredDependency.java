package com.cozooo.dlc_manager.client.required;

public class DlcRequiredDependency {
    private final String identifier;
    private final DlcRequiredEntry entry;

    public DlcRequiredDependency(String identifier, DlcRequiredEntry entry) {
        this.identifier = identifier;
        this.entry = entry;
    }

    public String getIdentifier() {
        return identifier;
    }

    public DlcRequiredEntry getEntry() {
        return entry;
    }
}
