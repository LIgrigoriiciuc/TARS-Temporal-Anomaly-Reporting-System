package com.tars.model.mappers;

import com.tars.model.Agent;
import com.tars.model.Supervisor;
import com.tars.model.User;
import com.tars.model.dto.UserRegistrationDTO;
import com.tars.model.dto.UserResponseDTO;

public class UserMapper {

    public static UserResponseDTO toDto(User user) {
        if (user == null) return null;

        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus().name());
        dto.setRole(user instanceof Agent ? "AGENT" : "SUPERVISOR");
        return dto;
    }

    public static User toEntity(UserRegistrationDTO dto) {
        if (dto == null) return null;

        User user = "SUPERVISOR".equalsIgnoreCase(dto.getRole()) ? new Supervisor() : new Agent();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        return user;
    }
}
