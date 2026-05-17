package com.tars.config;

import com.tars.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Handles all JWT operations: generate, validate, parse.
 * JWT = header.payload.signature
 * Signed with HMAC-SHA512 using a shared secret.
 */
@Component
public class JwtUtils {

    // Must be at least 512 bits for HS512.
    // In production: inject via @Value from environment variable.
    private final String jwtSecret = "tars_multiverse_jwt_secret_key_2026_must_be_long_enough_for_hs512_algorithm_ok";
    private final int jwtExpirationMs = 28800000; // 8 hours — NFR-03

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // Generate token directly from User entity — no Spring Authentication wrapper needed
    public String generateJwtTokenForUser(User user) {
        String role = user.getAuthorities().iterator().next().getAuthority();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // Extract email (subject) from token payload
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // Verify signature and expiration — returns false if tampered or expired
    public boolean validateJwtToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }
}