package com.sevis.userservice.service;

import com.sevis.userservice.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SessionCleanupService {

    private final UserSessionRepository sessionRepository;

    @Scheduled(cron = "${session.cleanup-cron:0 */5 * * * *}")
    public void pruneExpiredSessions() {
        sessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
