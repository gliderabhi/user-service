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
        this.token       = token;
        this.userId      = user.getId();
        this.name        = user.getName();
        this.email       = user.getEmail();
        this.role        = user.getRole().name();
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
