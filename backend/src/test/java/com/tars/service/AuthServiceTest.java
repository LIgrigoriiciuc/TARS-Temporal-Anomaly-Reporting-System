package com.tars.service;

import com.tars.config.JwtUtils;
import com.tars.model.Agent;
import com.tars.model.User;
import com.tars.model.dto.LoginRequestDTO;
import com.tars.model.dto.LoginResponseDTO;
import com.tars.model.enums.UserStatus;
import com.tars.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private TokenDenylistService tokenDenylistService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @InjectMocks
    private AuthService authService;
    private LoginRequestDTO validDto;
    private User mockUser;

    @BeforeEach
    void setUp() {
        validDto = new LoginRequestDTO();
        validDto.setEmail("agent@tars.com");
        validDto.setPassword("rawPassword123");
        mockUser = new Agent();
        mockUser.setId(1L);
        mockUser.setEmail("agent@tars.com");
        mockUser.setPassword("hashedPasswordBCrypt");
        mockUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void login_Success() {
        String mockJwt = "mocked.jwt.token";
        when(userRepository.findByEmail(validDto.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validDto.getPassword(), mockUser.getPassword())).thenReturn(true);
        when(jwtUtils.generateJwtTokenForUser(mockUser)).thenReturn(mockJwt);
        when(jwtUtils.getExpirationMs()).thenReturn(28800000L); // 8 hours
        LoginResponseDTO responseDto = authService.login(validDto, response);
        assertNotNull(responseDto);
        assertEquals(mockUser.getId(), responseDto.getId());
        assertEquals("Agent", responseDto.getRole());
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        Cookie capturedCookie = cookieCaptor.getValue();
        assertEquals("jwt", capturedCookie.getName());
        assertEquals(mockJwt, capturedCookie.getValue());
        assertTrue(capturedCookie.isHttpOnly());
        assertEquals("/", capturedCookie.getPath());
    }

    @Test
    void login_UserNotFound_ThrowsUnauthorized() {
        when(userRepository.findByEmail(validDto.getEmail())).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            authService.login(validDto, response);
        });
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid credentials", exception.getReason());
        verify(response, never()).addCookie(any(Cookie.class));
    }

    @Test
    void login_BadCredentials_ThrowsUnauthorized() {
        when(userRepository.findByEmail(validDto.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validDto.getPassword(), mockUser.getPassword())).thenReturn(false);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            authService.login(validDto, response);
        });
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid credentials", exception.getReason());
        verify(response, never()).addCookie(any(Cookie.class));
    }

    @Test
    void login_InactiveAccount_ThrowsForbidden() {
        mockUser.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmail(validDto.getEmail())).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(validDto.getPassword(), mockUser.getPassword())).thenReturn(true);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            authService.login(validDto, response);
        });
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Account is inactive", exception.getReason());
        verify(response, never()).addCookie(any(Cookie.class));
    }

    @Test
    void logout_WithValidToken_BlacklistsAndClearsCookie() {
        String jwtToken = "active.jwt.string";
        Cookie activeCookie = new Cookie("jwt", jwtToken);
        when(request.getCookies()).thenReturn(new Cookie[]{activeCookie});
        long futureTime = System.currentTimeMillis() + 600000;
        when(jwtUtils.getExpirationDateFromToken(jwtToken)).thenReturn(new Date(futureTime));
        authService.logout(request, response);
        verify(tokenDenylistService, times(1)).blacklistToken(eq(jwtToken), anyLong());
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        Cookie clearanceCookie = cookieCaptor.getValue();
        assertEquals("jwt", clearanceCookie.getName());
        assertEquals("", clearanceCookie.getValue());
        assertEquals(0, clearanceCookie.getMaxAge());
    }

    @Test
    void logout_NoCookiePresent_DoesNotBlacklistButClearsCookie() {
        when(request.getCookies()).thenReturn(null);
        authService.logout(request, response);
        verify(tokenDenylistService, never()).blacklistToken(anyString(), anyLong());
        verify(response, times(1)).addCookie(any(Cookie.class));
    }
}