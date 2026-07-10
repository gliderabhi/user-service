package com.sevis.userservice.service;

import com.sevis.userservice.dto.response.UserResponse;
import com.sevis.userservice.model.mapper.UserMapper;
import com.sevis.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Cacheable(value = "allUsers", sync = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    @Cacheable(value = "userById", key = "#id", sync = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Caching(evict = {
            @CacheEvict(value = "userById", key = "#id"),
            @CacheEvict(value = "allUsers", allEntries = true)
    })
    public UserResponse update(Long id, Map<String, String> body) {
        return userRepository.findById(id).map(user -> {
            if (body.containsKey("name"))        user.setName(body.get("name"));
            if (body.containsKey("phone"))       user.setPhone(body.get("phone"));
            if (body.containsKey("companyName")) user.setCompanyName(body.get("companyName"));
            return UserMapper.toResponse(userRepository.save(user));
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // Password itself isn't part of the cached UserResponse, but the entity
    // was written, so evict its by-id entry per the "evict aggressively on
    // writes" rule rather than relying on reasoning about which fields the
    // DTO happens to expose today.
    @CacheEvict(value = "userById", key = "#userId")
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Caching(evict = {
            @CacheEvict(value = "userById", key = "#id"),
            @CacheEvict(value = "allUsers", allEntries = true)
    })
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(id);
    }
}
