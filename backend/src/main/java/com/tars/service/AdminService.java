package com.tars.service;

import com.tars.model.User;
import com.tars.model.dto.UserRegistrationDTO;
import com.tars.model.enums.UserStatus;
import com.tars.model.mappers.UserMapper;
import com.tars.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

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

    private final JavaMailSender mailSender;

    public User createUser(UserRegistrationDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = UserMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        // UC-03 step 7: send credentials email
        sendCredentialsEmail(dto.getEmail(), dto.getName(), dto.getPassword());

        return saved;
    }

    private void sendCredentialsEmail(String email, String name, String rawPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("TARS // ACCESS_GRANTED");
            message.setText(
                    "TEMPORAL_ANOMALY_REPORTING_SYSTEM\n" +
                            "================================\n\n" +
                            "Personnel: " + name + "\n" +
                            "IDENTITY_HASH: " + email + "\n" +
                            "ACCESS_KEY: " + rawPassword + "\n\n" +
                            "RESTRICTED ACCESS // LEVEL 4 CLEARANCE\n" +
                            "Report to your supervisor immediately.\n\n" +
                            "-- TARS SYSTEM AUTO-GENERATED --"
            );
            mailSender.send(message);
        } catch (Exception e) {
            // UC-03 E1: SMTP down — log error but don't fail account creation
            System.err.println("CRITICAL MAIL ERROR: " + e.getMessage());
        }
    }
}
