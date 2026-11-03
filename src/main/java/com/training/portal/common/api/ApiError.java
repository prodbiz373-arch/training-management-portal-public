package com.training.portal.common.api;

import java.util.List;

public record ApiError(String code, String message, List<FieldIssue> errors) {
    public record FieldIssue(String field, String message) {}
}
