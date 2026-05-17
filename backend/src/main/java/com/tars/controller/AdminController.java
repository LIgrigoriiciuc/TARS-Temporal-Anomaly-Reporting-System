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

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(
                adminService.getAllUsers()
                        .stream()
                        .map(UserMapper::toDto)
                        .collect(Collectors.toList())
        );
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRequestDTO dto) {
        User user = UserMapper.toEntity(dto);
        User saved = adminService.createUser(user, dto.getPassword(), dto.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toDto(saved));
    }

    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        adminService.deactivateUser(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }
}