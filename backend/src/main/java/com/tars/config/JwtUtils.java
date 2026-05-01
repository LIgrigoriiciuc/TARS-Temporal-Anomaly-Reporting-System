package com.tars.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    // Must be at least 512 bits for HS512
    private final String jwtSecret = "tars_multiverse_jwt_secret_key_2026_must_be_long_enough_for_hs512_algorithm_ok";
    private final int jwtExpirationMs = 28800000; // 8 hours — NFR-03

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateJwtToken(Authentication authentication) {
        com.tars.model.User userPrincipal = (com.tars.model.User) authentication.getPrincipal();
        String role = userPrincipal.getAuthorities().iterator().next().getAuthority();

        return Jwts.builder()
                .subject(userPrincipal.getEmail())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

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
