package com.sevis.userservice.dto.request;

import com.sevis.userservice.model.User;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SignupRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^[+]?[0-9]{7,15}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter")
    @Pattern(regexp = ".*[0-9].*", message = "Password must contain at least one digit")
    private String password;

    @NotNull(message = "Role is required (DEALER or CUSTOMER)")
    private User.Role role;

    @NotNull(message = "Account type is required (INDIVIDUAL or COMPANY)")
    private User.AccountType accountType;

    private String companyName;

    // Dealer-specific fields
    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
             message = "Invalid GST number format (e.g. 22AAAAA0000A1Z5)")
    private String gstNo;

    private String address;
    private String city;
    private String state;

    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid 6-digit pincode")
    private String pinCode;

    private String dealerCode;
}
