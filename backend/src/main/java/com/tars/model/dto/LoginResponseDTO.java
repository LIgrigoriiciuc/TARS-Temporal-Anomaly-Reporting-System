package com.tars.model.dto;

@Data
public class LoginResponseDTO {
    private String token; // generated JWT
    private String email;
    private String role; // "Agent" / "Supervisor"
}