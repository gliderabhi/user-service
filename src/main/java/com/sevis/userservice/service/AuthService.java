package com.sevis.userservice.service;

import com.sevis.userservice.dto.request.LoginRequest;
import com.sevis.userservice.dto.request.SignupRequest;
import com.sevis.userservice.dto.response.AuthResponse;
import com.sevis.userservice.model.User;
import com.sevis.userservice.model.UserSession;
import com.sevis.userservice.model.mapper.UserMapper;
import com.sevis.userservice.repository.UserRepository;
import com.sevis.userservice.repository.UserSessionRepository;
import com.sevis.userservice.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;

    public Map<String, Object> signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (req.getAccountType() == User.AccountType.COMPANY &&
                (req.getCompanyName() == null || req.getCompanyName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Company name is required for COMPANY account type");
        }

        User saved = userRepository.save(UserMapper.toEntity(req, passwordEncoder));
        return Map.of("message", "User registered successfully", "userId", saved.getId());
    }

    public AuthResponse login(LoginRequest req, HttpServletRequest httpRequest) {
        User user;
        try {
            var auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
            user = (User) auth.getPrincipal();
        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Single-device enforcement: invalidate existing session
        sessionRepository.deleteByUserId(user.getId());

        String sessionId = jwtUtil.generateSessionId();
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole().name(), user.getAccountType().name(), sessionId, user.getRateLimit());

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionId(sessionId);
        session.setDeviceInfo(httpRequest.getHeader("User-Agent"));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        sessionRepository.save(session);

        return new AuthResponse(token, user);
    }

    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No token provided");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.isValid(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token");
        }
        sessionRepository.findBySessionId(jwtUtil.getSessionId(token))
                .ifPresent(sessionRepository::delete);
    }
}
