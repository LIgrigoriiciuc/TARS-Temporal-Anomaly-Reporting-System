package com.tars.config;

import com.tars.model.Agent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private Agent agent;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();

        agent = new Agent();
        agent.setId(1L);
        agent.setEmail("agent@tars.com");
        agent.setName("Test Agent");
        agent.setPassword("$hashed$");
    }

    // NFR-03: JWT generation and validation
    @Test
    void generateJwtToken_ValidToken() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                agent, null, agent.getAuthorities());

        String token = jwtUtils.generateJwtToken(auth);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void validateJwtToken_ValidToken_ReturnsTrue() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                agent, null, agent.getAuthorities());

        String token = jwtUtils.generateJwtToken(auth);

        assertTrue(jwtUtils.validateJwtToken(token));
    }

    @Test
    void validateJwtToken_InvalidToken_ReturnsFalse() {
        assertFalse(jwtUtils.validateJwtToken("invalid.token.here"));
    }

    @Test
    void validateJwtToken_EmptyToken_ReturnsFalse() {
        assertFalse(jwtUtils.validateJwtToken(""));
    }

    @Test
    void getUserNameFromJwtToken_ReturnsEmail() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                agent, null, agent.getAuthorities());

        String token = jwtUtils.generateJwtToken(auth);
        String email = jwtUtils.getUserNameFromJwtToken(token);

        assertEquals("agent@tars.com", email);
    }

    // NFR-03: JWT expires after 8 hours
    @Test
    void getExpirationMs_Is8Hours() {
        long eightHoursMs = 8 * 60 * 60 * 1000L;
        assertEquals(eightHoursMs, jwtUtils.getExpirationMs());
    }

    @Test
    void validateJwtToken_TamperedToken_ReturnsFalse() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                agent, null, agent.getAuthorities());

        String token = jwtUtils.generateJwtToken(auth);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwtUtils.validateJwtToken(tampered));
    }
}