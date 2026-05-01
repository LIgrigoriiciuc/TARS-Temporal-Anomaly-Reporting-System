package com.tars.controller;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPERVISOR')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @PostMapping("/users")
    public ResponseEntity<UserResponseDTO> createUser(@RequestBody UserRegistrationDTO dto) {
        User user = UserMapper.toEntity(dto);
        return ResponseEntity.ok(UserMapper.toDto(adminService.createUser(user)));
    }

    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        adminService.deactivateUser(id);
        return ResponseEntity.ok(new MessageResponse("User deactivated."));
    }
}
