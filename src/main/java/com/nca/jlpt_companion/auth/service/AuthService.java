package com.nca.jlpt_companion.auth.service;

import com.nca.jlpt_companion.auth.dto.LoginRequest;
import com.nca.jlpt_companion.auth.dto.RefreshRequest;
import com.nca.jlpt_companion.auth.dto.RegisterRequest;
import com.nca.jlpt_companion.auth.dto.TokenResponse;
import com.nca.jlpt_companion.auth.model.RefreshTokenEntity;
import com.nca.jlpt_companion.auth.repo.RefreshTokenRepo;
import com.nca.jlpt_companion.common.config.AppJwtProperties;
import com.nca.jlpt_companion.common.exception.AppExceptions;
import com.nca.jlpt_companion.users.model.UserEntity;
import com.nca.jlpt_companion.users.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepo userRepo;
    private final RefreshTokenRepo refreshRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtService;
    private final AppJwtProperties props;

    public TokenResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new AppExceptions.ConflictException("Email already used");
        }
        UUID userId = UUID.randomUUID();
        String hash = passwordEncoder.encode(req.password());
        var user = new UserEntity(userId, req.email(), hash, req.displayName());
        userRepo.save(user);
        return issueTokens(user);
    }

    public TokenResponse login(LoginRequest req) {
        UserEntity user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new AppExceptions.AuthFailedException("Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AppExceptions.AuthFailedException("Invalid email or password");
        }
        return issueTokens(user);
    }

    public TokenResponse refresh(RefreshRequest req) {
        String tokenPlain = req.refreshToken();
        String tokenHash = sha256(tokenPlain);
        RefreshTokenEntity row = refreshRepo.findByTokenHash(tokenHash)
                .filter(RefreshTokenEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        var user = userRepo.findById(row.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        // rotate: revoke old and issue new
        row.revoke();
        refreshRepo.save(row);
        return issueTokens(user);
    }

    public Optional<UserEntity> me(UUID userId) {
        return userRepo.findById(userId);
    }

    // ===== helpers =====
    private TokenResponse issueTokens(UserEntity user) {
        String access = jwtService.issueAccessToken(user.getId(), user.getEmail());
        String refreshPlain = UUID.randomUUID() + "-" + UUID.randomUUID();
        String refreshHash = sha256(refreshPlain);
        var refreshRow = new RefreshTokenEntity(
                UUID.randomUUID(), user.getId(), refreshHash,
                OffsetDateTime.now().plusDays(props.getRefreshExpDays())
        );
        refreshRepo.save(refreshRow);
        long expiresIn = props.getAccessExpMinutes() * 60;
        return new TokenResponse(access, refreshPlain, "Bearer", expiresIn);
    }

    private static String sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
