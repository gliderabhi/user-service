package com.sevis.userservice.dto.request;

import com.sevis.userservice.model.User;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Submitted by the "complete your profile" page shown after a Google
 * sign-in for an identity with no role yet in the target app. The idToken
 * is re-verified server-side — email is never taken from the request body,
 * only from the verified Google token, so this can't be used to claim an
 * arbitrary email.
 */
@Data
public class GoogleCompleteRequest {

    @NotBlank(message = "idToken is required")
    private String idToken;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Pattern(regexp = "^[+]?[0-9]{7,15}$", message = "Invalid phone number")
    private String phone;

    @NotNull(message = "Role is required")
    private User.Role role;

    @NotNull(message = "Account type is required (INDIVIDUAL or COMPANY)")
    private User.AccountType accountType;

    private String companyName;

    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
             message = "Invalid GST number format (e.g. 22AAAAA0000A1Z5)")
    private String gstNo;

    private String address;
    private String city;
    private String state;

    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid 6-digit pincode")
    private String pinCode;

    private String dealerCode;
    private Long dealerId;

    // Which app this completion is for — same semantics as SignupRequest.appId.
    private String appId;
}
