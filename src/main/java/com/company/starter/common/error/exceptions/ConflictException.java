package com.company.starter.common.error.exceptions;

import com.company.starter.common.error.ErrorCode;

public class ConflictException extends RuntimeException {

    private final ErrorCode errorCode;

    public ConflictException(String message) {
        this(message, ErrorCode.RESOURCE_ALREADY_EXISTS);
    }

    public ConflictException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
