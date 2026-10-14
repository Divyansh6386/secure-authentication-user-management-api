package com.company.starter.common.error.exceptions;

import com.company.starter.common.error.ErrorCode;

public class UnauthorizedException extends RuntimeException {

    private final ErrorCode errorCode;

    public UnauthorizedException(String message) {
        this(message, ErrorCode.AUTH_UNAUTHORIZED);
    }

    public UnauthorizedException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
