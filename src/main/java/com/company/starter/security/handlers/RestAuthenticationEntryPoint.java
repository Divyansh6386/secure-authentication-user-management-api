package com.company.starter.security.handlers;

import com.company.starter.common.error.ErrorCode;
import com.company.starter.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;
        HttpStatus httpStatus = errorCode.getHttpStatus();

        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                errorCode.name(),
                "Unauthorized",
                request.getRequestURI(),
                null);

        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getWriter(), body);
    }
}
