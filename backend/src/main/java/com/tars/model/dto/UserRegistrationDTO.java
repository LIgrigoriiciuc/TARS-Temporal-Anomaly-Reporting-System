package com.tars.model.dto;

import lombok.Data;

@Data
public class UserRegistrationDTO {
    private String name;
    private String email;
    private String password;
    private String role; // "AGENT" or "SUPERVISOR"
}
