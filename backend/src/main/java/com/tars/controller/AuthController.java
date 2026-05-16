package com.tars.controller;

import com.tars.config.JwtUtils;
import com.tars.model.User;
import com.tars.model.dto.LoginRequestDTO;
import com.tars.model.dto.LoginResponseDTO;
import com.tars.service.TokenDenylistService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final TokenDenylistService tokenDenylistService;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // UC-01: Login
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO loginRequest,
                                                   HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            // NFR-13: JWT stored as HttpOnly cookie — JS cannot access it
            Cookie cookie = new Cookie("jwt", jwt);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge((int) (jwtUtils.getExpirationMs() / 1000)); // seconds
            response.addCookie(cookie);

            User user = (User) authentication.getPrincipal();
            String role = user.getAuthorities().iterator().next().getAuthority();
            log.info("LOGIN_SUCCESS | user={}", loginRequest.getEmail());
            return ResponseEntity.ok(new LoginResponseDTO(user.getEmail(), role));
        }catch (Exception e) {
            log.warn("LOGIN_FAILURE | user={} | reason={}", loginRequest.getEmail(), e.getMessage());
            throw e;
        }
    }

    // UC-02: Logout
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String jwt = parseJwt(request);
        String email = "unknown";
        if (jwt != null) {
            // Add to Redis denylist — NFR-13
            email = jwtUtils.getUserNameFromJwtToken(jwt);
            tokenDenylistService.blacklistToken(jwt, jwtUtils.getExpirationMs());

        }
        log.info("LOGOUT | user={}", email);

        // Clear the cookie
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    private String parseJwt(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
//    @GetMapping("/hash")
//    public String hash() {
//        return new BCryptPasswordEncoder(12).encode("password123");
//    }
}
