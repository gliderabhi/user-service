package com.sevis.userservice.controller;

import com.sevis.userservice.dto.request.LoginRequest;
import com.sevis.userservice.dto.request.SignupRequest;
import com.sevis.userservice.service.AuthService;
import com.sevis.userservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(req));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(req, httpRequest));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return ResponseEntity.ok(authService.googleLogin(body.get("idToken"), request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, String> body) {
        userService.changePassword(userId, body.get("currentPassword"), body.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
