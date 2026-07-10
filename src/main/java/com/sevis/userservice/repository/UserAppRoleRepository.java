package com.sevis.userservice.repository;

import com.sevis.userservice.model.UserAppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAppRoleRepository extends JpaRepository<UserAppRole, Long> {
    Optional<UserAppRole> findByUserIdAndAppId(Long userId, String appId);
    boolean existsByUserIdAndAppId(Long userId, String appId);
}
