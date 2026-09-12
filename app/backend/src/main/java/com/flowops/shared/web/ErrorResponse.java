package com.flowops.shared.web;

import java.util.List;

public record ErrorResponse(String code, String message, List<FieldViolation> details) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }

    public static ErrorResponse of(String code, String message, List<FieldViolation> details) {
        return new ErrorResponse(code, message, List.copyOf(details));
    }

    public record FieldViolation(String field, String rule) {}
}
