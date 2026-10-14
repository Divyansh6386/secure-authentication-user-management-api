package com.company.starter.common.error;

public record FieldErrorResponse(
        String field,
        String message
) {
}
