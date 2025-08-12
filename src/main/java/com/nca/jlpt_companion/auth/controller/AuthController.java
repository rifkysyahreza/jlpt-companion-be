package com.nca.jlpt_companion.auth.controller;

import com.nca.jlpt_companion.auth.dto.*;
import com.nca.jlpt_companion.auth.service.AuthService;
import com.nca.jlpt_companion.users.model.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "JWT authentication")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register new user and get tokens")
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @Operation(summary = "Login with email/password and get tokens")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @Operation(summary = "Get current user profile (requires Authorization: Bearer)")
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        UserEntity user = authService.me(userId).orElseThrow();
        return ResponseEntity.ok(new MeResponse(user.getId(), user.getEmail(), user.getDisplayName()));
    }
}
