package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.exception.LoginBlockedException;
import com.akhana.akhana_admin.service.LoginAttemptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory failed login counter per username and per IP. The app runs as a single instance on the VPS,
 * so memory is enough; counters are lost on restart, which only shortens an active lock.
 */
@Service
@Slf4j
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final int PURGE_THRESHOLD = 10_000;

    private final int maxAttemptsPerUser;
    private final int maxAttemptsPerIp;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;
    private final String blockedMessage;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptServiceImpl(
            @Value("${security.login.max-attempts-per-user:5}") int maxAttemptsPerUser,
            @Value("${security.login.max-attempts-per-ip:20}") int maxAttemptsPerIp,
            @Value("${security.login.window:15m}") Duration window,
            @Value("${security.login.lockout:15m}") Duration lockout) {
        this(maxAttemptsPerUser, maxAttemptsPerIp, window, lockout, Clock.systemUTC());
    }

    public LoginAttemptServiceImpl(int maxAttemptsPerUser, int maxAttemptsPerIp,
                                   Duration window, Duration lockout, Clock clock) {
        this.maxAttemptsPerUser = maxAttemptsPerUser;
        this.maxAttemptsPerIp = maxAttemptsPerIp;
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
        long minutes = Math.max(1, (lockout.toSeconds() + 59) / 60);
        this.blockedMessage = "Demasiados intentos fallidos. Espere " + minutes
            + (minutes == 1 ? " minuto" : " minutos") + " e intente nuevamente.";
    }

    @Override
    public void checkNotBlocked(String username, String clientIp) {
        Instant now = clock.instant();
        if (isLocked(userKey(username), now) || isLocked(ipKey(clientIp), now)) {
            throw new LoginBlockedException(blockedMessage);
        }
    }

    @Override
    public void recordFailure(String username, String clientIp) {
        Instant now = clock.instant();
        purgeExpiredIfLarge(now);
        boolean userLocked = registerFailure(userKey(username), maxAttemptsPerUser, now);
        boolean ipLocked = registerFailure(ipKey(clientIp), maxAttemptsPerIp, now);
        if (userLocked || ipLocked) {
            log.warn("Login locked for {} (user '{}', ip {})", lockout, username, clientIp);
            throw new LoginBlockedException(blockedMessage);
        }
    }

    @Override
    public void recordSuccess(String username) {
        attempts.remove(userKey(username));
    }

    private boolean isLocked(String key, Instant now) {
        Attempts current = attempts.get(key);
        return current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil());
    }

    private boolean registerFailure(String key, int maxAttempts, Instant now) {
        Attempts updated = attempts.compute(key, (k, current) -> {
            if (current == null || current.isExpired(now, window)) {
                current = new Attempts(0, now, null);
            }
            int failures = current.failures() + 1;
            Instant lockedUntil = failures >= maxAttempts ? now.plus(lockout) : null;
            return new Attempts(failures, current.windowStart(), lockedUntil);
        });
        return updated.lockedUntil() != null;
    }

    private void purgeExpiredIfLarge(Instant now) {
        if (attempts.size() > PURGE_THRESHOLD) {
            attempts.values().removeIf(a -> a.isExpired(now, window));
        }
    }

    private static String userKey(String username) {
        return "user:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String clientIp) {
        return "ip:" + (clientIp == null ? "unknown" : clientIp);
    }

    private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {
        /** A lock lasts until lockedUntil; without a lock, failures count only inside the window. */
        boolean isExpired(Instant now, Duration window) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return !now.isBefore(windowStart.plus(window));
        }
    }
}
