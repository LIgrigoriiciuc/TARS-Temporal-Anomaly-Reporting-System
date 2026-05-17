package com.tars.config;

package com.tars.config;

import com.tars.model.Agent;
import com.tars.model.User;
import com.tars.repository.UserRepository;
import com.tars.service.TokenDenylistService;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Plain servlet filter — runs on every HTTP request.
 * Replaces Spring Security's filter chain entirely.
 * Responsibilities:
 *   1. Allow public endpoints without token
 *   2. Extract and validate JWT from HttpOnly cookie
 *   3. Check Redis denylist (logout + deactivation)
 *   4. Enforce role-based access per endpoint prefix
 *   5. Attach authenticated user to request for controllers
 */
@Component
@RequiredArgsConstructor
public class JwtFilter implements Filter {

    private final JwtUtils jwtUtils;
    private final TokenDenylistService tokenDenylistService;
    private final UserRepository userRepository;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path = request.getServletPath();
        // Public endpoints — no token required
        if (isPublic(path)) {
            chain.doFilter(req, res);
            return;
        }
        // Extract JWT from HttpOnly cookie
        String jwt = extractJwt(request);
        if (jwt == null || !jwtUtils.validateJwtToken(jwt)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid token");
            return;
        }
        // Check token denylist — covers logout
        if (tokenDenylistService.isTokenBlacklisted(jwt)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalidated");
            return;
        }
        // Load user from DB
        String email = jwtUtils.getUserNameFromJwtToken(jwt);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            return;
        }
        // Check user denylist — covers account deactivation while logged in
        if (tokenDenylistService.isUserBlacklisted(user.getId())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account deactivated");
            return;
        }
        // Determine role from Java type
        String role = user instanceof Agent ? "Agent" : "Supervisor";
        // Role-based access control per endpoint prefix
        if (path.startsWith("/api/admin/") && !role.equals("Supervisor")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Supervisor access required");
            return;
        }
        if (path.startsWith("/api/reports/") && !role.equals("Agent")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Agent access required");
            return;
        }
        // Attach user to request — controllers retrieve via request.getAttribute("currentUser")
        request.setAttribute("currentUser", user);
        chain.doFilter(req, res);
    }

    private boolean isPublic(String path) {
        return path.startsWith("/api/auth/") || path.startsWith("/ws/");
    }

    private String extractJwt(HttpServletRequest request) {
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