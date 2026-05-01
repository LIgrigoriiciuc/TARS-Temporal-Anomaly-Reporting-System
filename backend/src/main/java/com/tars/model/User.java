package com.tars.model;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@Data @NoArgsConstructor @AllArgsConstructor
public abstract class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // Aici va sta hash-ul BCrypt

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;
}

