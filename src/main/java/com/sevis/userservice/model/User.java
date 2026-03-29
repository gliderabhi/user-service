package com.sevis.userservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User implements UserDetails {

    public enum Role        { DEALER, CUSTOMER, ADMIN }
    public enum AccountType { INDIVIDUAL, COMPANY }
    public enum Status      { ACTIVE, INACTIVE, SUSPENDED }

    /** Requests per minute allowed for this client. Configurable per user. */
    public static final int DEFAULT_RATE_INDIVIDUAL = 60;
    public static final int DEFAULT_RATE_COMPANY    = 300;
    public static final int DEFAULT_RATE_ADMIN      = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    private String companyName;

    // Dealer-specific fields
    private String gstNo;
    private String address;
    private String city;
    private String state;
    private String pinCode;
    private String dealerCode;

    /** Configurable rate limit (requests/min). Set at signup, adjustable per user. */
    @Column(nullable = false)
    private int rateLimit = DEFAULT_RATE_INDIVIDUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = Status.ACTIVE;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getUsername()              { return email; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return status != Status.SUSPENDED; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return status == Status.ACTIVE; }
}
