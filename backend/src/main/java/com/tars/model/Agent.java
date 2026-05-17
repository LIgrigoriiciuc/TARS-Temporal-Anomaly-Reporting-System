package com.tars.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "agents")
@DiscriminatorValue("Agent") //tells Hibernate/db what type of object to create when reading from the database
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor //without it, it can't deserialize from db
public class Agent extends User {

    private Integer monthlyReportCount = 0;

}
