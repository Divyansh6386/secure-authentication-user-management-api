package com.company.starter.auth.controller;

import com.company.starter.auth.dto.AuthResponse;
import com.company.starter.auth.dto.LoginRequest;
import com.company.starter.auth.dto.MessageResponse;
import com.company.starter.auth.dto.ProfileResponse;
import com.company.starter.auth.dto.RefreshRequest;
import com.company.starter.auth.dto.RegisterRequest;
import com.company.starter.auth.service.AuthService;
import com.company.starter.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(req.email(), req.password()));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req.email(), req.password()));
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout() {
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<MessageResponse> logoutAll() {
        UUID userId = SecurityUtils.currentUserId();
        authService.logoutAll(userId);
        return ResponseEntity.ok(new MessageResponse("Logged out from all sessions"));
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> profile() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(authService.profile(userId));
    }
}
