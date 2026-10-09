package com.cozooo.dclanguage;

import java.util.List;

public record DcValidationResult(List<DcIssue> issues) {
    public DcValidationResult {
        issues = List.copyOf(issues);
    }

    public boolean valid() { return issues.isEmpty(); }
}
