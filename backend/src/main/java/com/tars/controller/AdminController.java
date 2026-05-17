package com.tars.controller;

import com.tars.model.User;
import com.tars.model.dto.UserRegistrationDTO;
import com.tars.model.dto.UserResponseDTO;
import com.tars.model.mappers.UserMapper;
import com.tars.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // Get all users — Supervisor dashboard list
    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        List<UserResponseDTO> users = adminService.getAllUsers()
                .stream()
                .map(UserMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // UC-03: Create user account
    @PostMapping("/users")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRegistrationDTO dto) {
        User created = adminService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toDto(created));
    }

    // UC-04: Deactivate user account
    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id, HttpServletRequest request) {
        // Get currently logged-in supervisor from request attribute set by JwtFilter
        User currentUser = (User) request.getAttribute("currentUser");
        adminService.deactivateUser(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }
}