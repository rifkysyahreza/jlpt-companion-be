package com.nca.jlpt_companion.auth.dto;

import java.util.UUID;

public record MeResponse(UUID userId, String email, String displayName) {}
