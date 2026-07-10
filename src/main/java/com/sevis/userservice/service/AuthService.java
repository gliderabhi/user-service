package com.sevis.userservice.service;

import com.sevis.userservice.dto.request.GoogleCompleteRequest;
import com.sevis.userservice.dto.request.LoginRequest;
import com.sevis.userservice.dto.request.SignupRequest;
import com.sevis.userservice.dto.response.AuthResponse;
import com.sevis.userservice.model.User;
import com.sevis.userservice.model.UserAppRole;
import com.sevis.userservice.model.UserSession;
import com.sevis.userservice.model.mapper.UserMapper;
import com.sevis.userservice.repository.UserAppRoleRepository;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserAppRoleRepository userAppRoleRepository;
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

    // The original app — signup/login for this appId keeps its exact original
    // behavior (single global role on User, blocks on duplicate email) so none
    // of its existing users/clients are affected by the multi-app role model.
    private static final String LEGACY_APP_ID = "SEVIS_AUTO";

    private static String normalizeAppId(String raw) {
        return (raw == null || raw.isBlank()) ? LEGACY_APP_ID : raw.trim().toUpperCase();
    }

    public Map<String, Object> signup(SignupRequest req) {
        String appId = normalizeAppId(req.getAppId());

        if (LEGACY_APP_ID.equals(appId)) {
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

        // Non-legacy app: the identity (User row — email/name/password) may
        // already exist from another app. Reuse it rather than blocking on
        // "email already registered", but require the correct existing
        // password first — otherwise anyone could "link" a new app role onto
        // someone else's email without knowing their real credentials.
        Optional<User> existing = userRepository.findByEmail(req.getEmail());
        User user;
        if (existing.isPresent()) {
            user = existing.get();
            if (user.getPassword() == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "An account with this email already exists — log in with your existing password to link this app");
            }
        } else {
            User u = new User();
            u.setName(req.getName());
            u.setEmail(req.getEmail());
            u.setPhone(req.getPhone());
            u.setPassword(passwordEncoder.encode(req.getPassword()));
            // Identity-level placeholder — the real, app-scoped role lives in UserAppRole below.
            u.setRole(User.Role.CUSTOMER);
            u.setAccountType(req.getAccountType() != null ? req.getAccountType() : User.AccountType.INDIVIDUAL);
            u.setRateLimit(User.DEFAULT_RATE_INDIVIDUAL);
            u.setStatus(User.Status.ACTIVE);
            user = userRepository.save(u);
        }

        if (userAppRoleRepository.existsByUserIdAndAppId(user.getId(), appId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already registered for this app");
        }

        UserAppRole appRole = new UserAppRole();
        appRole.setUserId(user.getId());
        appRole.setAppId(appId);
        appRole.setRole(req.getRole().name());
        userAppRoleRepository.save(appRole);

        return Map.of("message", "User registered successfully", "userId", user.getId());
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

        String appId = normalizeAppId(req.getAppId());
        String role = resolveRole(user, appId);

        // Single-device enforcement: invalidate existing session
        sessionRepository.deleteByUserId(user.getId());

        String sessionId = jwtUtil.generateSessionId();
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), role, user.getAccountType().name(), sessionId, user.getRateLimit(), user.getDealerId());

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionId(sessionId);
        session.setDeviceInfo(httpRequest.getHeader("User-Agent"));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        sessionRepository.save(session);

        return new AuthResponse(token, user, role);
    }

    // For the legacy app, role is just the identity's single top-level role
    // (unchanged behavior). For any other app, the identity must already have
    // signed up there — Google login handles first-time auto-provisioning
    // separately, since it's meant to be a frictionless one-click flow.
    private String resolveRole(User user, String appId) {
        if (LEGACY_APP_ID.equals(appId)) {
            return user.getRole().name();
        }
        return userAppRoleRepository.findByUserIdAndAppId(user.getId(), appId)
                .map(UserAppRole::getRole)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No account found for this app — please sign up first"));
    }

    // Google login only ever authenticates an identity that already has a
    // role for this app — a brand-new identity, or an existing identity
    // that's new to this particular app, gets a clean NOT_FOUND so the
    // frontend can route to the "complete your profile" page and call
    // completeGoogleSignup() below. No silent auto-provisioning: a person
    // should always get to choose their role/details, not have one guessed.
    public AuthResponse googleLogin(String idToken, HttpServletRequest httpRequest, boolean longLived, String appIdRaw) {
        GoogleTokenVerifier.GoogleUserInfo info = googleTokenVerifier.verify(idToken);
        String appId = normalizeAppId(appIdRaw);

        User user = userRepository.findByEmail(info.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No account found — please complete signup"));

        if (user.getStatus() == User.Status.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended");
        }

        if (user.getGoogleId() == null) {
            user.setGoogleId(info.googleId());
            userRepository.save(user);
        }

        String role = resolveRole(user, appId);

        sessionRepository.deleteByUserId(user.getId());

        String sessionId = jwtUtil.generateSessionId();
        String token = longLived
                ? jwtUtil.generateToken(
                        user.getEmail(), user.getId(), role, user.getAccountType().name(),
                        sessionId, user.getRateLimit(), user.getDealerId(), TV_SESSION_DURATION)
                : jwtUtil.generateToken(
                        user.getEmail(), user.getId(), role,
                        user.getAccountType().name(), sessionId, user.getRateLimit(), user.getDealerId());

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionId(sessionId);
        session.setDeviceInfo(httpRequest.getHeader("User-Agent"));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setExpiresAt(longLived ? LocalDateTime.now().plus(TV_SESSION_DURATION) : LocalDateTime.now().plusDays(1));
        sessionRepository.save(session);

        return new AuthResponse(token, user, role);
    }

    // Finishes signup after googleLogin() returned NOT_FOUND. Re-verifies the
    // idToken (email is only ever taken from the verified token, never from
    // the request body) then either creates the identity (legacy app, first
    // time ever) or links a new per-app role onto an existing identity
    // (non-legacy app). No password is set for Google-only identities —
    // User.password stays null and they keep authenticating via Google.
    public AuthResponse completeGoogleSignup(GoogleCompleteRequest req, HttpServletRequest httpRequest) {
        GoogleTokenVerifier.GoogleUserInfo info = googleTokenVerifier.verify(req.getIdToken());
        String appId = normalizeAppId(req.getAppId());

        Optional<User> existing = userRepository.findByEmail(info.email());
        User user;
        String role;

        if (LEGACY_APP_ID.equals(appId)) {
            if (existing.isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists — please log in");
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

            User u = new User();
            u.setName(req.getName());
            u.setEmail(info.email());
            u.setPhone(req.getPhone());
            u.setGoogleId(info.googleId());
            u.setRole(req.getRole());
            u.setAccountType(req.getAccountType());
            u.setCompanyName(req.getCompanyName());
            u.setGstNo(req.getGstNo());
            u.setAddress(req.getAddress());
            u.setCity(req.getCity());
            u.setState(req.getState());
            u.setPinCode(req.getPinCode());
            u.setDealerCode(req.getDealerCode());
            u.setDealerId(req.getDealerId());
            u.setRateLimit(UserMapper.defaultRateLimit(req.getRole(), req.getAccountType()));
            u.setStatus(User.Status.ACTIVE);
            user = userRepository.save(u);
            role = user.getRole().name();
        } else {
            if (existing.isPresent()) {
                user = existing.get();
                if (user.getGoogleId() == null) {
                    user.setGoogleId(info.googleId());
                    userRepository.save(user);
                }
            } else {
                User u = new User();
                u.setName(req.getName());
                u.setEmail(info.email());
                u.setPhone(req.getPhone());
                u.setGoogleId(info.googleId());
                u.setRole(User.Role.CUSTOMER); // identity-level placeholder, see signup()
                u.setAccountType(User.AccountType.INDIVIDUAL);
                u.setRateLimit(User.DEFAULT_RATE_INDIVIDUAL);
                u.setStatus(User.Status.ACTIVE);
                user = userRepository.save(u);
            }

            if (userAppRoleRepository.existsByUserIdAndAppId(user.getId(), appId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Already registered for this app");
            }
            UserAppRole appRole = new UserAppRole();
            appRole.setUserId(user.getId());
            appRole.setAppId(appId);
            appRole.setRole(req.getRole().name());
            userAppRoleRepository.save(appRole);
            role = req.getRole().name();
        }

        sessionRepository.deleteByUserId(user.getId());
        String sessionId = jwtUtil.generateSessionId();
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), role, user.getAccountType().name(), sessionId, user.getRateLimit(), user.getDealerId());

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionId(sessionId);
        session.setDeviceInfo(httpRequest.getHeader("User-Agent"));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        sessionRepository.save(session);

        return new AuthResponse(token, user, role);
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
