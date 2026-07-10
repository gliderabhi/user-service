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
    private final String gstNo;
    private final String address;
    private final String city;
    private final String state;
    private final String pinCode;
    private final String dealerCode;

    public AuthResponse(String token, User user) {
        this(token, user, user.getRole().name());
    }

    // Used when the caller has resolved an app-scoped role (see AuthService's
    // per-app UserAppRole lookup) rather than the identity's legacy top-level role.
    public AuthResponse(String token, User user, String role) {
        this.token       = token;
        this.userId      = user.getId();
        this.name        = user.getName();
        this.email       = user.getEmail();
        this.role        = role;
        this.accountType = user.getAccountType().name();
        this.companyName = user.getCompanyName();
        this.gstNo       = user.getGstNo();
        this.address     = user.getAddress();
        this.city        = user.getCity();
        this.state       = user.getState();
        this.pinCode     = user.getPinCode();
        this.dealerCode  = user.getDealerCode();
    }
}
