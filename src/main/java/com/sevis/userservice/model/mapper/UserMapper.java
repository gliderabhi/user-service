package com.sevis.userservice.model.mapper;

import com.sevis.userservice.dto.request.SignupRequest;
import com.sevis.userservice.dto.response.UserResponse;
import com.sevis.userservice.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserMapper {

    public static User toEntity(SignupRequest req, PasswordEncoder encoder) {
        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setRole(req.getRole());
        user.setAccountType(req.getAccountType());
        user.setCompanyName(req.getCompanyName());
        user.setRateLimit(defaultRateLimit(req.getRole(), req.getAccountType()));
        return user;
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user);
    }

    private static int defaultRateLimit(User.Role role, User.AccountType accountType) {
        if (role == User.Role.ADMIN)               return User.DEFAULT_RATE_ADMIN;
        if (accountType == User.AccountType.COMPANY) return User.DEFAULT_RATE_COMPANY;
        return User.DEFAULT_RATE_INDIVIDUAL;
    }

    private UserMapper() {}
}
