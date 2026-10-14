package com.company.starter.common.error;

import com.company.starter.common.error.exceptions.BadRequestException;
import com.company.starter.common.error.exceptions.ConflictException;
import com.company.starter.common.error.exceptions.ForbiddenException;
import com.company.starter.common.error.exceptions.NotFoundException;
import com.company.starter.common.error.exceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<FieldErrorResponse> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorResponse(
                        fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"))
                .toList();

        return buildError(ErrorCode.VALIDATION_ERROR, "Validation failed", request, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return buildError(ErrorCode.BAD_REQUEST, "Invalid parameter: " + ex.getName(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return buildError(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type. Please use Content-Type: application/json",
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return buildError(
                ErrorCode.METHOD_NOT_ALLOWED,
                ex.getMessage() != null ? ex.getMessage() : "Method not allowed",
                request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return buildError(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return buildError(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return buildError(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildError(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildError(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return buildError(ErrorCode.ACCESS_DENIED, "Access denied", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildError(ErrorCode.BAD_REQUEST, "Request body is missing or invalid", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        return buildError(ErrorCode.RESOURCE_ALREADY_EXISTS, "Resource already exists", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return buildError(
                ErrorCode.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "Invalid request",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return buildError(ErrorCode.INTERNAL_ERROR, "Unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildError(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request) {
        return buildError(errorCode, message, request, null);
    }

    private ResponseEntity<ErrorResponse> buildError(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<FieldErrorResponse> details) {

        HttpStatus httpStatus = errorCode.getHttpStatus();

        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                errorCode.name(),
                message,
                request.getRequestURI(),
                details);

        return ResponseEntity.status(httpStatus).body(body);
    }
}
