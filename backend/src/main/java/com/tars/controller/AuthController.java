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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final TokenDenylistService tokenDenylistService;

    // UC-01: Login
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO loginRequest,
                                                   HttpServletResponse response) {
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

        return ResponseEntity.ok(new LoginResponseDTO(user.getEmail(), role));
    }

    // UC-02: Logout
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String jwt = parseJwt(request);

        if (jwt != null) {
            // Add to Redis denylist — NFR-13
            tokenDenylistService.blacklistToken(jwt, jwtUtils.getExpirationMs());
        }

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
}
