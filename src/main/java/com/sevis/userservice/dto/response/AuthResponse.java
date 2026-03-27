package com.sevis.userservice.dto.response;

import com.sevis.userservice.model.User;
import lombok.Getter;

@Getter
public class AuthResponse {
    private final String token;
    private final Long userId;
    private final String name;
    private final String email;
    private final String role;
    private final String accountType;
    private final String companyName;

    public AuthResponse(String token, User user) {
        this.token       = token;
        this.userId      = user.getId();
        this.name        = user.getName();
        this.email       = user.getEmail();
        this.role        = user.getRole().name();
        this.accountType = user.getAccountType().name();
        this.companyName = user.getCompanyName();
    }
}
