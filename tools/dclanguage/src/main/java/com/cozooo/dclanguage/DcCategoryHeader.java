package com.cozooo.dclanguage;

import java.util.List;

public record DcCategoryHeader(List<String> names, int start, int end, int indentation, boolean bracketed) {
    public DcCategoryHeader {
        names = List.copyOf(names);
    }
}
