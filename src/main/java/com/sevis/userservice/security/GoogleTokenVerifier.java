package com.sevis.userservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class GoogleTokenVerifier {

    private final List<String> allowedClientIds;
    private final RestTemplate restTemplate = new RestTemplate();

    // Bound via SpEL split rather than a plain @Value list injection — a YAML
    // sequence has no flat property key for @Value to resolve directly, so a
    // bare ${...} reference would silently fall back to empty instead of the
    // configured entries. See application.yml: the property is a
    // comma-separated string, not a YAML list, specifically to make this work.
    public GoogleTokenVerifier(@Value("#{'${google.client-ids}'.split(',')}") List<String> allowedClientIds) {
        this.allowedClientIds = allowedClientIds.stream().map(String::trim).toList();
    }

    public record GoogleUserInfo(String googleId, String email, String name) {}

    @SuppressWarnings("unchecked")
    public GoogleUserInfo verify(String idToken) {
        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
        Map<String, Object> payload;
        try {
            payload = restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
        }

        // An ID token's audience matches whichever OAuth client obtained it — the
        // web app's Sign-In button and the TV app's device-code flow use different
        // client IDs, so either is accepted here rather than just one.
        if (payload == null || !allowedClientIds.contains(payload.get("aud"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token audience mismatch");
        }

        String email = (String) payload.get("email");
        String name  = (String) payload.getOrDefault("name", email);
        String sub   = (String) payload.get("sub");

        if (email == null || sub == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token missing required fields");
        }

        return new GoogleUserInfo(sub, email, name);
    }
}
