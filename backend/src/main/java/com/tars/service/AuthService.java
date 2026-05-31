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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenDenylistService tokenDenylistService;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO dto, HttpServletResponse response) {
        // Step 1: load user from DB
        User user = userRepository.findByEmail(dto.getEmail()).orElseThrow(() -> {
            log.warn("LOGIN_FAILURE | user={} | reason=User not found", dto.getEmail());
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        });
        // Step 2: verify password with BCrypt
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("LOGIN_FAILURE | user={} | reason=Bad credentials", dto.getEmail());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        // Step 3: check account status
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.warn("LOGIN_FAILURE | user={} | reason=Account inactive", dto.getEmail());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
        }
        // Step 4: generate JWT
        String jwt = jwtUtils.generateJwtTokenForUser(user);
        // Step 5: set HttpOnly cookie
        Cookie cookie = getCookie(jwt);
        //From this point on, every request the browser makes to localhost:8080 includes the cookie automatically in the Cookie header, and the filter reads it
        // the token is never touched in Angular; the browser handles it invisibly
        response.addCookie(cookie);
        log.info("LOGIN_SUCCESS | user={}", user.getEmail());
        String role = user instanceof Agent ? "Agent" : "Supervisor";
        return new LoginResponseDTO(user.getId(), user.getEmail(), role);
    }

    @NonNull
    private Cookie getCookie(String jwt) {
        Cookie cookie = new Cookie("jwt", jwt);
        //Marks it HttpOnly so JavaScript cannot read it, document.cookie won't show it (XSS protection)
        cookie.setHttpOnly(true);
        //Cookie is sent on every request to any path on this domain, not just /api/auth.
        cookie.setPath("/");
        //Cookie lives for 8 hours in the browser
        cookie.setMaxAge((int) (jwtUtils.getExpirationMs() / 1000));
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String jwt = extractJwtFromCookies(request);
        String email = "unknown";
        if (jwt != null) {
            try {
                Date expirationDate = jwtUtils.getExpirationDateFromToken(jwt);
                long timeLeftMs = expirationDate.getTime() - System.currentTimeMillis();
                if (timeLeftMs > 0) {
                    tokenDenylistService.blacklistToken(jwt, timeLeftMs);
                }
            } catch (Exception e) {
                // UC-02 E1 — Redis unreachable: token cannot be blacklisted server-side,
                // but cookie is still cleared below so the browser session ends.
                // Token remains valid until natural expiry.
                log.error("DENYLIST_FAILURE | token could not be blacklisted | {}", e.getMessage());
            }
        }
        log.info("LOGOUT | user={}", email);
        //Clear cookie
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        //this effectively deletes it from the browser immediately
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public String extractJwtFromCookies(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}