package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.Supervisor;
import com.tars.model.User;
import com.tars.model.enums.UserStatus;
import com.tars.repository.AgentRepository;
import com.tars.repository.SupervisorRepository;
import com.tars.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private SupervisorRepository supervisorRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private JavaMailSender mailSender;
    @Mock private TokenDenylistService tokenDenylistService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private AdminService adminService;

    private Agent agentEntity;
    private Supervisor supervisorEntity;

    @BeforeEach
    void setUp() {
        agentEntity = new Agent();
        agentEntity.setName("Test Agent");
        agentEntity.setEmail("agent@tars.com");

        supervisorEntity = new Supervisor();
        supervisorEntity.setName("Test Supervisor");
        supervisorEntity.setEmail("supervisor@tars.com");
    }

    // UC-03: Create agent — happy path
    @Test
    void createUser_Agent_Success() {
        when(userRepository.existsByEmail("agent@tars.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$hashed$");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        User result = adminService.createUser(agentEntity, "password123", "agent@tars.com");

        assertNotNull(result);
        assertInstanceOf(Agent.class, result);
        assertEquals("agent@tars.com", result.getEmail());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
        assertEquals("$hashed$", result.getPassword());
        verify(userRepository).save(any(User.class));
    }

    // UC-03: Create supervisor — happy path
    @Test
    void createUser_Supervisor_Success() {
        when(userRepository.existsByEmail("supervisor@tars.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$hashed$");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        User result = adminService.createUser(supervisorEntity, "password123", "supervisor@tars.com");

        assertInstanceOf(Supervisor.class, result);
    }

    // UC-03 A1: Duplicate email — throws 409 Conflict
    @Test
    void createUser_DuplicateEmail_ThrowsConflict() {
        when(userRepository.existsByEmail("agent@tars.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminService.createUser(agentEntity, "password123", "agent@tars.com"));

        assertEquals(409, ex.getStatusCode().value());
        verify(userRepository, never()).save(any());
    }

    // UC-04: Deactivate user — happy path
    @Test
    void deactivateUser_Success() {
        Agent target = new Agent();
        target.setId(2L);
        target.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doNothing().when(tokenDenylistService).blacklistUser(2L);
        doNothing().when(messagingTemplate).convertAndSend(anyString(), (Object) any());

        adminService.deactivateUser(2L, 1L);

        assertEquals(UserStatus.INACTIVE, target.getStatus());
        verify(userRepository).save(target);
        verify(tokenDenylistService).blacklistUser(2L);
        verify(messagingTemplate).convertAndSend(anyString(), (Object) any());
    }

    // UC-04 E1: Supervisor cannot deactivate own account — throws 403
    @Test
    void deactivateUser_OwnAccount_ThrowsForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminService.deactivateUser(1L, 1L));

        assertEquals(403, ex.getStatusCode().value());
        verify(userRepository, never()).findById(any());
    }

    // UC-04: Target not found — throws 404
    @Test
    void deactivateUser_NotFound_ThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminService.deactivateUser(99L, 1L));

        assertEquals(404, ex.getStatusCode().value());
    }

    // UC-04 PRE-3: Already inactive — throws 400
    @Test
    void deactivateUser_AlreadyInactive_ThrowsBadRequest() {
        Agent target = new Agent();
        target.setId(2L);
        target.setStatus(UserStatus.INACTIVE);

        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminService.deactivateUser(2L, 1L));

        assertEquals(400, ex.getStatusCode().value());
    }

    // UC-03 E1: SMTP down — account still created, no exception thrown
    @Test
    void createUser_SmtpDown_AccountStillCreated() {
        when(userRepository.existsByEmail("agent@tars.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$hashed$");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        User result = assertDoesNotThrow(
                () -> adminService.createUser(agentEntity, "password123", "agent@tars.com"));
        assertNotNull(result);
    }
}