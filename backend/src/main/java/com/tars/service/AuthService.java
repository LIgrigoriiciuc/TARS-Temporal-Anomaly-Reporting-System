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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenDenylistService tokenDenylistService;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // UC-01: Login — manual authentication without Spring AuthenticationManager
    public LoginResponseDTO login(LoginRequestDTO dto, HttpServletResponse response) {

        // Step 1: load user from DB
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> {
                    log.warn("LOGIN_FAILURE | user={} | reason=User not found", dto.getEmail());
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });

        // Step 2: verify password with BCrypt
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("LOGIN_FAILURE | user={} | reason=Bad credentials", dto.getEmail());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        // Step 3: check account status
        if ( ((com.tars.model.User) user).getStatus() == UserStatus.INACTIVE) {
            log.warn("LOGIN_FAILURE | user={} | reason=Account inactive", dto.getEmail());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
        }

        // Step 4: generate JWT
        String jwt = jwtUtils.generateJwtTokenForUser(user);

        // Step 5: set HttpOnly cookie — NFR-13
        Cookie cookie = new Cookie("jwt", jwt);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtUtils.getExpirationMs() / 1000));
        response.addCookie(cookie);

        log.info("LOGIN_SUCCESS | user={}", user.getEmail());

        String role = user instanceof Agent ? "Agent" : "Supervisor";
        return new LoginResponseDTO(user.getId(), user.getEmail(), role);
    }

    // UC-02: Logout — blacklist token and clear cookie
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String jwt = extractJwtFromCookies(request);
        String email = "unknown";

        if (jwt != null) {
            email = jwtUtils.getUserNameFromJwtToken(jwt);
            tokenDenylistService.blacklistToken(jwt, jwtUtils.getExpirationMs());
        }

        log.info("LOGOUT | user={}", email);

        // Clear cookie server-side
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
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