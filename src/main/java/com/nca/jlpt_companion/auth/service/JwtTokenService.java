package com.nca.jlpt_companion.auth.service;

import com.nca.jlpt_companion.common.config.AppJwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final String issuer;
    private final SecretKey key;
    private final long accessExpMinutes;

    public JwtTokenService(AppJwtProperties props) {
        this.issuer = props.getIssuer();
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes());
        this.accessExpMinutes = props.getAccessExpMinutes();
    }

    public String issueAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessExpMinutes * 60);
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public UUID validateAndGetUserId(String jwt) {
        var jwtParser = Jwts.parser().verifyWith(key).build();
        var claims = jwtParser.parseSignedClaims(jwt).getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
