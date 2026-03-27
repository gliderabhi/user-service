package com.sevis.userservice.dto.response;

import com.sevis.userservice.model.User;
import lombok.Getter;

@Getter
public class UserResponse {
    private final Long id;
    private final String name;
    private final String email;
    private final String phone;
    private final String role;
    private final String accountType;
    private final String companyName;
    private final String status;

    public UserResponse(User user) {
        this.id          = user.getId();
        this.name        = user.getName();
        this.email       = user.getEmail();
        this.phone       = user.getPhone();
        this.role        = user.getRole().name();
        this.accountType = user.getAccountType().name();
        this.companyName = user.getCompanyName();
        this.status      = user.getStatus().name();
    }
}
