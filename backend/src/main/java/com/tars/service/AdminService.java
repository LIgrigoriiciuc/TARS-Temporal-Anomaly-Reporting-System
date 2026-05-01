package com.tars.service;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;

    public User createUser(User newUser) {
        if(userRepository.findByEmail(newUser.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered!");
        }
        newUser.setPassword(encoder.encode(newUser.getPassword())); // NFR-04
        newUser.setStatus(UserStatus.ACTIVE);
        return userRepository.save(newUser);
    }

    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        // UC-04: can't deactivate itself (implemented in Controller usually)
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }
}