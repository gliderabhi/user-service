package com.sevis.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    // Which app this login is for (e.g. "ROOMLIST"). Omitted/blank defaults to
    // the legacy SEVIS_AUTO app for backward compatibility with existing clients.
    private String appId;
}
