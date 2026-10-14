package com.company.starter.common.error.exceptions;

import com.company.starter.common.error.ErrorCode;

public class NotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    public NotFoundException(String message) {
        this(message, ErrorCode.RESOURCE_NOT_FOUND);
    }

    public NotFoundException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
