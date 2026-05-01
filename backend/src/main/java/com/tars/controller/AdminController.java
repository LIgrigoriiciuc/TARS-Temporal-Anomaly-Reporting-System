package com.tars.controller;

import com.tars.model.User;
import com.tars.model.dto.UserRegistrationDTO;
import com.tars.model.dto.UserResponseDTO;
import com.tars.model.mappers.UserMapper;
import com.tars.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // Get all users (user list view)
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
    public ResponseEntity<UserResponseDTO> createUser(@RequestBody UserRegistrationDTO dto) {
        User created = adminService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toDto(created));
    }

    // UC-04: Deactivate user account
    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        adminService.deactivateUser(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }
}
