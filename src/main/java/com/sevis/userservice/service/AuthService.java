package com.sevis.userservice.service;

import com.sevis.userservice.dto.request.LoginRequest;
import com.sevis.userservice.dto.request.SignupRequest;
import com.sevis.userservice.dto.response.AuthResponse;
import com.sevis.userservice.model.User;
import com.sevis.userservice.model.UserSession;
import com.sevis.userservice.model.mapper.UserMapper;
import com.sevis.userservice.repository.UserRepository;
import com.sevis.userservice.repository.UserSessionRepository;
import com.sevis.userservice.security.GoogleTokenVerifier;
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

import java.time.Duration;
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
    private final GoogleTokenVerifier googleTokenVerifier;

    // A TV isn't carried around and re-authenticated the way a phone is — the
    // device-code sign-in flow requesting a fresh QR/code every day would be
    // poor UX for something meant to just sit there signed in. 365 days here
    // is "effectively persistent" while still bounded, rather than a token
    // that never expires at all.
    private static final Duration TV_SESSION_DURATION = Duration.ofDays(365);

    public Map<String, Object> signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (req.getAccountType() == User.AccountType.COMPANY &&
                (req.getCompanyName() == null || req.getCompanyName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Company name is required for COMPANY account type");
        }
        if (req.getRole() == User.Role.DEALER &&
                (req.getGstNo() == null || req.getGstNo().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "GST number is required for DEALER accounts");
        }
        if (req.getRole() == User.Role.TECHNICIAN && req.getAccountType() == null) {
            req.setAccountType(User.AccountType.INDIVIDUAL);
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
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole().name(), user.getAccountType().name(), sessionId, user.getRateLimit(), user.getDealerId());

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionId(sessionId);
        session.setDeviceInfo(httpRequest.getHeader("User-Agent"));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        sessionRepository.save(session);

        return new AuthResponse(token, user);
    }

    public AuthResponse googleLogin(String idToken, HttpServletRequest httpRequest, boolean longLived) {
        GoogleTokenVerifier.GoogleUserInfo info = googleTokenVerifier.verify(idToken);

        User user = userRepository.findByEmail(info.email())
                .orElseGet(() -> {
                    User u = new User();
                    u.setName(info.name());
                    u.setEmail(info.email());
                    u.setGoogleId(info.googleId());
                    u.setRole(User.Role.CUSTOMER);
                    u.setAccountType(User.AccountType.INDIVIDUAL);
                    u.setRateLimit(User.DEFAULT_RATE_INDIVIDUAL);
                    u.setStatus(User.Status.ACTIVE);
                    return userRepository.save(u);
                });

        if (user.getStatus() == User.Status.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended");
        }

        if (user.getGoogleId() == null) {
            user.setGoogleId(info.googleId());
            userRepository.save(user);
        }

        sessionRepository.deleteByUserId(user.getId());

        String sessionId = jwtUtil.generateSessionId();
        String token = longLived
                ? jwtUtil.generateToken(
                        user.getEmail(), user.getId(), user.getRole().name(), user.getAccountType().name(),
                        sessionId, user.getRateLimit(), user.getDealerId(), TV_SESSION_DURATION)
                : jwtUtil.generateToken(
                        user.getEmail(), user.getId(), user.getRole().name(),
                        user.getAccountType().name(), sessionId, user.getRateLimit(), user.getDealerId());

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionId(sessionId);
        session.setDeviceInfo(httpRequest.getHeader("User-Agent"));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setExpiresAt(longLived ? LocalDateTime.now().plus(TV_SESSION_DURATION) : LocalDateTime.now().plusDays(1));
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
