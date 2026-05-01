package com.tars.model.mappers;

import com.tars.model.Agent;
import com.tars.model.User;

public class UserMapper {
    public static UserResponseDTO toDto(User user) {
        if (user == null) return null;

        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus().name());
        // from image_4abcf5.jpg)
        dto.setRole(user instanceof Agent ? "AGENT" : "SUPERVISOR");
        return dto;
    }

    public static User toEntity(UserRegistrationDTO dto) {
        if (dto == null) return null;
        User user = dto.getRole().equalsIgnoreCase("SUPERVISOR") ? new Supervisor() : new Agent();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        return user;
    }
}