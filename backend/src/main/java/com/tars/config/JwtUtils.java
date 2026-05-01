package com.tars.config;

import com.tars.model.User;

@Component
public class JwtUtils {
    private String jwtSecret = "girly_secret_for_tars_multiverse_2026";
    private int jwtExpirationMs = 28800000; // 8 hours according to NFR-03

    public String generateJwtToken(Authentication authentication) {
        User userPrincipal = (User) authentication.getPrincipal();
        return Jwts.builder()
                .setSubject(userPrincipal.getEmail())
                .claim("role", userPrincipal.getClass().getSimpleName())
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }
}