package com.tars.service;

import com.tars.model.User;
import com.tars.model.dto.UserRegistrationDTO;
import com.tars.model.enums.UserStatus;
import com.tars.model.mappers.UserMapper;
import com.tars.repository.AgentRepository;
import com.tars.repository.SupervisorRepository;
import com.tars.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AgentRepository agentRepository;
    private final SupervisorRepository supervisorRepository;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenDenylistService tokenDenylistService;
    private final SimpMessagingTemplate messagingTemplate;
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    // UC-04: Deactivate user
    public void deactivateUser(Long targetId, Long currentUserId) {
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
        tokenDenylistService.blacklistUser(targetId);

        // Notify via WebSocket
        messagingTemplate.convertAndSend("/topic/user-deactivated/" + targetId, "TERMINATED");

        log.info("USER_DEACTIVATED | targetId={} | by={}", targetId, currentUserId);
    }
    private final JavaMailSender mailSender;

    @Transactional
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
        log.info("USER_CREATED | email={} | role={}", dto.getEmail(), dto.getRole());
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
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        users.addAll(agentRepository.findAll());
        users.addAll(supervisorRepository.findAll());
        users.sort(Comparator.comparing(User::getId));
        return users;
    }
}
