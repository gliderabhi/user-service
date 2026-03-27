package com.sevis.userservice.repository;

import com.sevis.userservice.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findBySessionId(String sessionId);

    @Transactional
    void deleteByUserId(Long userId);
}
