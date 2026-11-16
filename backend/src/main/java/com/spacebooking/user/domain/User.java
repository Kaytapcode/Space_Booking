package com.spacebooking.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** UTC. The schema has no default, so the constructor assigns it. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User(String email, String passwordHash, String fullName, Role role, Clock clock) {
        this.id = UUID.randomUUID();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.createdAt = LocalDateTime.now(clock);
    }
}
