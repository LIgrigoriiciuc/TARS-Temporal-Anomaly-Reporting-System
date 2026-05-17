package com.tars.model.mappers;
import com.tars.model.Agent;
import com.tars.model.Supervisor;
import com.tars.model.User;
import com.tars.model.dto.UserRequestDTO;
import com.tars.model.dto.UserResponseDTO;

public class UserMapper {

    public static UserResponseDTO toDto(User user) {
        if (user == null) return null;

        return new UserResponseDTO(
                user.getId(), user.getName(), user.getEmail(), user instanceof Agent ? "AGENT" : "SUPERVISOR",
                user.getStatus().name()
        );
    }
    //no password!
    public static User toEntity(UserRequestDTO dto) {
        if (dto == null) return null;
        User user = "SUPERVISOR".equalsIgnoreCase(dto.getRole()) ? new Supervisor() : new Agent();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        return user;
    }
}
