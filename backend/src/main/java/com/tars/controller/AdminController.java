package com.tars.controller;

import com.tars.model.User;
import com.tars.model.dto.UserRequestDTO;
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

/**
 * Handles user management HTTP endpoints — UC-03, UC-04.
 * Responsibility: map request DTOs → entities, call service, map entities → response DTOs.
 * No business logic here — that belongs in AdminService.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // Get all users — Supervisor dashboard list
    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(
                adminService.getAllUsers()
                        .stream()
                        .map(UserMapper::toDto)                        // map each entity → response DTO
                        .collect(Collectors.toList())
        );
    }

    // UC-03: Create user account
    @PostMapping("/users")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRequestDTO dto) {
        User user = UserMapper.toEntity(dto);                          // map request DTO → entity
        User saved = adminService.createUser(user, dto.getPassword(), dto.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toDto(saved)); // map entity → response
    }

    // UC-04: Deactivate user account
    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser"); // set by JwtFilter
        adminService.deactivateUser(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }
}