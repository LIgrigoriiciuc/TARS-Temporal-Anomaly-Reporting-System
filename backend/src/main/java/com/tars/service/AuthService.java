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
import org.springframework.transaction.annotation.Transactional;
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
        //This string contains email, role, issued-at, expiration — all encoded and signed.
        String jwt = jwtUtils.generateJwtTokenForUser(user);
        // Step 5: set HttpOnly cookie — NFR-13
        //Creates a cookie named jwt with the token as value.
        Cookie cookie = new Cookie("jwt", jwt);
        //Marks it HttpOnly — JavaScript cannot read it. document.cookie won't show it. Only the browser transmits it automatically. This is the XSS protection from NFR-13.
        cookie.setHttpOnly(true);
        //Cookie is sent on every request to any path on this domain, not just /api/auth.
        cookie.setPath("/");
        //Cookie lives for 8 hours in the browser — divided by 1000 because maxAge is in seconds, expirationMs is in milliseconds.
        cookie.setMaxAge((int) (jwtUtils.getExpirationMs() / 1000));
        //Adds Set-Cookie: jwt=eyJ...; HttpOnly; Path=/; Max-Age=28800 to the HTTP response header. The browser reads this header and stores the cookie automatically.
        //From this point on — every request the browser makes to localhost:8080 includes the cookie automatically in the Cookie header. The filter reads it,
        // validates the JWT, sets authentication. You never touch the token in Angular — the browser handles it invisibly.
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
        //It effectively deletes it. maxAge=0 tells the browser "this cookie is expired, discard it." The browser removes it from storage.
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