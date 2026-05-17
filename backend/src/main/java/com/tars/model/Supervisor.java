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
@Table(name = "supervisors")
@DiscriminatorValue("Supervisor")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Supervisor extends User {

}
