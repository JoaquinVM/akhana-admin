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
 * In-memory failed login counter per username and IP pair and per IP. The app runs as a single instance on the VPS,
 * so memory is enough; counters are lost on restart, which only shortens an active lock. The app is on the internet
 * (specs/deploy.md): a username alone is never locked by wrong passwords, or anyone could lock out a user by guessing
 * its name. Wrong second factor codes are also capped per username from any IP (USR-07), since only whoever knows the
 * password reaches the code.
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
    private final int maxSecondFactorFailures;
    private final Duration secondFactorWindow;
    private final String secondFactorBlockedMessage;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptServiceImpl(
            @Value("${security.login.max-attempts-per-user:5}") int maxAttemptsPerUser,
            @Value("${security.login.max-attempts-per-ip:20}") int maxAttemptsPerIp,
            @Value("${security.login.window:15m}") Duration window,
            @Value("${security.login.lockout:15m}") Duration lockout,
            @Value("${security.login.second-factor.max-failures-per-user:10}") int maxSecondFactorFailures,
            @Value("${security.login.second-factor.window:1h}") Duration secondFactorWindow) {
        this(maxAttemptsPerUser, maxAttemptsPerIp, window, lockout, maxSecondFactorFailures, secondFactorWindow,
            Clock.systemUTC());
    }

    public LoginAttemptServiceImpl(int maxAttemptsPerUser, int maxAttemptsPerIp,
                                   Duration window, Duration lockout, Clock clock) {
        this(maxAttemptsPerUser, maxAttemptsPerIp, window, lockout, 10, Duration.ofHours(1), clock);
    }

    public LoginAttemptServiceImpl(int maxAttemptsPerUser, int maxAttemptsPerIp, Duration window, Duration lockout,
                                   int maxSecondFactorFailures, Duration secondFactorWindow, Clock clock) {
        this.maxAttemptsPerUser = maxAttemptsPerUser;
        this.maxAttemptsPerIp = maxAttemptsPerIp;
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
        this.blockedMessage = waitMessage("Demasiados intentos fallidos.", lockout);
        this.maxSecondFactorFailures = maxSecondFactorFailures;
        this.secondFactorWindow = secondFactorWindow;
        this.secondFactorBlockedMessage = waitMessage("Demasiados códigos incorrectos para este usuario.",
            secondFactorWindow);
    }

    private static String waitMessage(String reason, Duration wait) {
        long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
        return reason + " Espera " + minutes + (minutes == 1 ? " minuto" : " minutos") + " e intenta nuevamente.";
    }

    @Override
    public void checkNotBlocked(String username, String clientIp) {
        Instant now = clock.instant();
        if (isLocked(userKey(username, clientIp), now) || isLocked(ipKey(clientIp), now)) {
            throw new LoginBlockedException(blockedMessage);
        }
    }

    @Override
    public void recordFailure(String username, String clientIp) {
        Instant now = clock.instant();
        purgeExpiredIfLarge(now);
        boolean userLocked = registerFailure(userKey(username, clientIp), maxAttemptsPerUser, window, lockout, now);
        boolean ipLocked = registerFailure(ipKey(clientIp), maxAttemptsPerIp, window, lockout, now);
        if (userLocked || ipLocked) {
            log.warn("Login locked for {} (user '{}', ip {})", lockout, username, clientIp);
            throw new LoginBlockedException(blockedMessage);
        }
    }

    @Override
    public void recordSuccess(String username, String clientIp) {
        attempts.remove(userKey(username, clientIp));
    }

    @Override
    public void checkSecondFactorNotBlocked(String username) {
        if (isLocked(secondFactorKey(username), clock.instant())) {
            throw new LoginBlockedException(secondFactorBlockedMessage);
        }
    }

    @Override
    public void recordSecondFactorFailure(String username) {
        Instant now = clock.instant();
        purgeExpiredIfLarge(now);
        // A success does not reset it: the cap counts every wrong code of the hour, whoever typed it
        if (registerFailure(secondFactorKey(username), maxSecondFactorFailures, secondFactorWindow, secondFactorWindow,
                now)) {
            log.warn("Second factor locked for {} for user '{}' after {} wrong codes from any IP",
                secondFactorWindow, username, maxSecondFactorFailures);
            throw new LoginBlockedException(secondFactorBlockedMessage);
        }
    }

    private boolean isLocked(String key, Instant now) {
        Attempts current = attempts.get(key);
        return current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil());
    }

    private boolean registerFailure(String key, int maxAttempts, Duration window, Duration lockout, Instant now) {
        Attempts updated = attempts.compute(key, (k, current) -> {
            if (current == null || current.isExpired(now)) {
                current = new Attempts(0, now, null, window);
            }
            int failures = current.failures() + 1;
            Instant lockedUntil = failures >= maxAttempts ? now.plus(lockout) : null;
            return new Attempts(failures, current.windowStart(), lockedUntil, window);
        });
        return updated.lockedUntil() != null;
    }

    private void purgeExpiredIfLarge(Instant now) {
        if (attempts.size() > PURGE_THRESHOLD) {
            attempts.values().removeIf(a -> a.isExpired(now));
        }
    }

    private static String userKey(String username, String clientIp) {
        return "user:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT)) + "@" + ipKey(clientIp);
    }

    private static String secondFactorKey(String username) {
        return "totp:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String clientIp) {
        return "ip:" + (clientIp == null ? "unknown" : clientIp);
    }

    private record Attempts(int failures, Instant windowStart, Instant lockedUntil, Duration window) {
        /** A lock lasts until lockedUntil; without a lock, failures count only inside the window. */
        boolean isExpired(Instant now) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return !now.isBefore(windowStart.plus(window));
        }
    }
}
