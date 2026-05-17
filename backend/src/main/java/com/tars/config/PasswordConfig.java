package com.tars.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Password encoding configuration.
 * BCrypt with cost factor 12.
 * Cost factor 12 means 2^12 = 4096 hashing rounds, making brute-force expensive.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}