package com.tars.service;

import com.tars.model.Agent;
import com.tars.model.Supervisor;
import com.tars.model.User;
import com.tars.model.dto.UserRegistrationDTO;
import com.tars.model.enums.UserStatus;
import com.tars.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private AdminService adminService;

    private UserRegistrationDTO agentDTO;
    private UserRegistrationDTO supervisorDTO;

    @BeforeEach
    void setUp() {
        agentDTO = new UserRegistrationDTO();
        agentDTO.setName("Test Agent");
        agentDTO.setEmail("agent@tars.com");
        agentDTO.setPassword("password123");
        agentDTO.setRole("AGENT");

        supervisorDTO = new UserRegistrationDTO();
        supervisorDTO.setName("Test Supervisor");
        supervisorDTO.setEmail("supervisor@tars.com");
        supervisorDTO.setPassword("password123");
        supervisorDTO.setRole("SUPERVISOR");
    }

    // UC-03: Create user — happy path
    @Test
    void createUser_Agent_Success() {
        when(userRepository.existsByEmail("agent@tars.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$hashed$");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        User result = adminService.createUser(agentDTO);

        assertNotNull(result);
        assertInstanceOf(Agent.class, result);
        assertEquals("agent@tars.com", result.getEmail());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
        assertEquals("$hashed$", result.getPassword());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_Supervisor_Success() {
        when(userRepository.existsByEmail("supervisor@tars.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$hashed$");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        User result = adminService.createUser(supervisorDTO);

        assertInstanceOf(Supervisor.class, result);
    }

    // UC-03 A1: Duplicate email
    @Test
    void createUser_DuplicateEmail_ThrowsConflict() {
        when(userRepository.existsByEmail("agent@tars.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminService.createUser(agentDTO));

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

        adminService.deactivateUser(2L, 1L);

        assertEquals(UserStatus.INACTIVE, target.getStatus());
        verify(userRepository).save(target);
    }

    // UC-04 E1: Supervisor cannot deactivate own account
    @Test
    void deactivateUser_OwnAccount_ThrowsForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminService.deactivateUser(1L, 1L));

        assertEquals(403, ex.getStatusCode().value());
        verify(userRepository, never()).findById(any());
    }

    // UC-04: Target not found
    @Test
    void deactivateUser_NotFound_ThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> adminService.deactivateUser(99L, 1L));

        assertEquals(404, ex.getStatusCode().value());
    }

    // UC-04 PRE-3: Already inactive
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

    // UC-03 E1: SMTP down — account still created
    @Test
    void createUser_SmtpDown_AccountStillCreated() {
        when(userRepository.existsByEmail("agent@tars.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$hashed$");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should NOT throw — UC-03 E1 says account is created even if mail fails
        User result = assertDoesNotThrow(() -> adminService.createUser(agentDTO));
        assertNotNull(result);
    }
}