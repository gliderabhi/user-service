package com.sevis.userservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Per-app role for a shared identity. A single User (one email, one login)
 * can hold a different role in each app — e.g. DEALER in SEVIS_AUTO and
 * BROKER in ROOMLIST — without the apps' role vocabularies colliding.
 */
@Getter
@Setter
@Entity
@Table(name = "user_app_roles")
public class UserAppRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String role;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
