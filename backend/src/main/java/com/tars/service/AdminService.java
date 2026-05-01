package com.tars.service;

import com.tars.model.User;
import com.tars.model.dto.UserRegistrationDTO;
import com.tars.model.enums.UserStatus;
import com.tars.model.mappers.UserMapper;
import com.tars.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // UC-03: Create user
    public User createUser(UserRegistrationDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = UserMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword())); // NFR-04
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    // UC-04: Deactivate user
    public void deactivateUser(Long targetId, Long currentUserId) {
        // UC-04 E1: Supervisor cannot deactivate their own account
        if (targetId.equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot deactivate your own account");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already inactive");
        }

        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
