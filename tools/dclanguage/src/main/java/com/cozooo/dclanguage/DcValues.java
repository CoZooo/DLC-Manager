package com.cozooo.dclanguage;

import com.google.gson.JsonElement;

final class DcValues {
    private DcValues() { }
    static boolean same(JsonElement first, JsonElement second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first.isJsonNull() || second.isJsonNull()) return first.isJsonNull() && second.isJsonNull();
        if (first.isJsonObject() && second.isJsonObject()) {
            if (!first.getAsJsonObject().keySet().equals(second.getAsJsonObject().keySet())) return false;
            for (var entry : first.getAsJsonObject().entrySet()) {
                if (!same(entry.getValue(), second.getAsJsonObject().get(entry.getKey()))) return false;
            }
            return true;
        }
        if (first.isJsonArray() && second.isJsonArray()) {
            if (first.getAsJsonArray().size() != second.getAsJsonArray().size()) return false;
            for (int i = 0; i < first.getAsJsonArray().size(); i++) {
                if (!same(first.getAsJsonArray().get(i), second.getAsJsonArray().get(i))) return false;
            }
            return true;
        }
        if (!first.isJsonPrimitive() || !second.isJsonPrimitive()) return false;
        var a = first.getAsJsonPrimitive();
        var b = second.getAsJsonPrimitive();
        if (a.isNumber() || b.isNumber()) {
            if (!a.isNumber() || !b.isNumber()) return false;
            try { return a.getAsBigDecimal().compareTo(b.getAsBigDecimal()) == 0; }
            catch (NumberFormatException exception) { return false; }
        }
        return a.equals(b);
    }
}
