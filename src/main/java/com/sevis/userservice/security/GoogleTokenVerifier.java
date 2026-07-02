package com.sevis.userservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class GoogleTokenVerifier {

    private final String clientId;
    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        this.clientId = clientId;
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

        if (payload == null || !clientId.equals(payload.get("aud"))) {
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
