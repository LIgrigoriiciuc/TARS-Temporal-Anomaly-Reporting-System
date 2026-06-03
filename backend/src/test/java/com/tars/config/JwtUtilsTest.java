package com.tars.config;

import com.tars.model.Agent;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void generateJwtToken_ValidToken() {
        String token = jwtUtils.generateJwtTokenForUser(agent);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void validateJwtToken_ValidToken_ReturnsTrue() {
        String token = jwtUtils.generateJwtTokenForUser(agent);
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
        String token = jwtUtils.generateJwtTokenForUser(agent);
        String email = jwtUtils.getUserNameFromJwtToken(token);
        assertEquals("agent@tars.com", email);
    }

    @Test
    void getExpirationMs_Is8Hours() {
        long eightHoursMs = 8 * 60 * 60 * 1000L;
        assertEquals(eightHoursMs, jwtUtils.getExpirationMs());
    }

    @Test
    void validateJwtToken_TamperedToken_ReturnsFalse() {
        String token = jwtUtils.generateJwtTokenForUser(agent);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtils.validateJwtToken(tampered));
    }
    @Test
    void getExpirationDateFromToken_ReturnsValidFutureDate() {
        long currentTimeMs = System.currentTimeMillis();
        String token = jwtUtils.generateJwtTokenForUser(agent);
        java.util.Date expirationDate = jwtUtils.getExpirationDateFromToken(token);
        assertNotNull(expirationDate);
        long expectedTargetTimeMs = currentTimeMs + jwtUtils.getExpirationMs();
        long variance = Math.abs(expirationDate.getTime() - expectedTargetTimeMs);
        assertTrue(variance < 2000, "The token expiration date deviated too far from the 8-hour rule. Variance was: " + variance + "ms");
    }

    @Test
    void getExpirationDateFromToken_InvalidToken_ThrowsException() {
        assertThrows(JwtException.class, () -> {
            jwtUtils.getExpirationDateFromToken("malicious.garbage.token");
        });
    }

}