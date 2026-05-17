package com.tars.service;

import com.tars.model.User;
import com.tars.model.enums.UserStatus;
import com.tars.repository.AgentRepository;
import com.tars.repository.SupervisorRepository;
import com.tars.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AgentRepository agentRepository;
    private final SupervisorRepository supervisorRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenDenylistService tokenDenylistService;
    private final SimpMessagingTemplate messagingTemplate;
    private final JavaMailSender mailSender;
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    @Transactional
    public User createUser(User user, String rawPassword, String email) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        sendCredentialsEmail(saved.getEmail(), saved.getName(), rawPassword);
        log.info("USER_CREATED | email={}", saved.getEmail());
        return saved;
    }

    // invalidates session immediately via Redis + WebSocket
    @Transactional
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
        // invalidate active JWT immediately
        tokenDenylistService.blacklistUser(targetId);
        // Notify agent via WebSocket, triggers redirect to log in in Angular
        messagingTemplate.convertAndSend("/topic/user-deactivated/" + targetId, "TERMINATED");
        log.info("USER_DEACTIVATED | targetId={} | by={}", targetId, currentUserId);
    }

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        users.addAll(agentRepository.findAll());
        users.addAll(supervisorRepository.findAll());
        users.sort(Comparator.comparing(User::getId));
        return users;
    }
    @Async
    protected void sendCredentialsEmail(String email, String name, String rawPassword) {
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
                            "Report immediately.\n\n" +
                            "-- TARS SYSTEM AUTO-GENERATED --"
            );
            mailSender.send(message);
        } catch (Exception e) { //logs error but does NOT fail account creation
            log.error("CRITICAL MAIL ERROR: {}", e.getMessage());
        }
    }
}