package com.company.starter.common.error.exceptions;

import com.company.starter.common.error.ErrorCode;

public class ForbiddenException extends RuntimeException {

    private final ErrorCode errorCode;

    public ForbiddenException(String message) {
        this(message, ErrorCode.ACCESS_DENIED);
    }

    public ForbiddenException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
