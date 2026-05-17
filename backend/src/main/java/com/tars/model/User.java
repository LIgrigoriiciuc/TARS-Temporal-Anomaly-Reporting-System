package com.tars.model;

import com.tars.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
//two inserts + fk to pk user table in the child table at insert
//select with join at get
@Inheritance(strategy = InheritanceType.JOINED)
//dtype in users tells Hibernate what kind of object to create
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@Data //generates getters/setters/equals/hashCode/toString
@NoArgsConstructor
@AllArgsConstructor //constructors generated automatically
public abstract class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //generated automatically by db
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE; //creating in-memory

}
