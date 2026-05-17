package com.tars.config;

import com.tars.model.Agent;
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

    private final int jwtExpirationMs = 28800000; // 8 hours

    private SecretKey getSigningKey() {
        //at least 64 bytes
        String jwtSecret = "tars_multiverse_jwt_secret_key_2026_must_be_long_enough_for_hs512_algorithm_ok";
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // Generate token directly from User entity
    public String generateJwtTokenForUser(User user) {
        String role = user instanceof Agent ? "Agent" : "Supervisor";
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact(); //mashes them all together into the SHA-512 blender to create the signature
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

    // Verify signature and expiration, returns false if tampered or expired
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