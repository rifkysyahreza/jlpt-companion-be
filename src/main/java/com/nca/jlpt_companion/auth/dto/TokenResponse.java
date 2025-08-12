package com.nca.jlpt_companion.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType, // "Bearer"
        long expiresIn    // seconds
) {}
