package com.tars.config;

import com.tars.model.Agent;
import com.tars.model.User;
import com.tars.repository.UserRepository;
import com.tars.service.TokenDenylistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenDenylistService tokenDenylistService;
    private final UserRepository userRepository; // direct DB access, no UserDetailsService

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String jwt = extractJwtFromCookies(request);

        if (jwt != null && jwtUtils.validateJwtToken(jwt)) {

            // Check if token was explicitly invalidated (logout)
            if (tokenDenylistService.isTokenBlacklisted(jwt)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalidated");
                return;
            }

            String email = jwtUtils.getUserNameFromJwtToken(jwt);

            // Load user directly from DB — no UserDetailsService abstraction
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
                return;
            }

            // UC-04 POST-2: check if user was deactivated while logged in
            if (tokenDenylistService.isUserBlacklisted(user.getId())) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account deactivated");
                return;
            }
// Set authentication in Spring Security context
            String role = user instanceof Agent ? "Agent" : "Supervisor";
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);}

        filterChain.doFilter(request, response);
    }

    // Exclude login endpoint — old cookie should not block new login attempts
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().equals("/api/auth/login");
    }

    private String extractJwtFromCookies(HttpServletRequest request) {
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