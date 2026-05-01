package com.tars.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponseDTO {
    private String email;
    private String role; // "Agent" or "Supervisor"
}
