package com.tars.controller;

import com.tars.config.JwtUtils;

import java.net.http.HttpHeaders;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> authenticate(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        String jwt = jwtUtils.generateJwtToken(authentication);

        ResponseCookie cookie = ResponseCookie.from("tars-token", jwt)
                .httpOnly(true)
                .secure(false) // true in production
                .path("/")
                .maxAge(8 * 60 * 60) // 8 hours NFR-03
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new MessageResponse("Login succesful!"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // UC-02: delete cookie and add in denylist
        ResponseCookie cookie = ResponseCookie.from("tars-token", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new MessageResponse("Logged out."));
    }
}