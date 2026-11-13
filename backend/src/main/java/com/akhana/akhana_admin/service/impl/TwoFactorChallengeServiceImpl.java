package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.service.TwoFactorChallengeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TwoFactorChallengeServiceImpl implements TwoFactorChallengeService {

    static final Duration LIFETIME = Duration.ofMinutes(5);
    static final int MAX_FAILURES = 5;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Open> challenges = new ConcurrentHashMap<>();

    @Autowired
    public TwoFactorChallengeServiceImpl() {
        this(Clock.systemUTC());
    }

    public TwoFactorChallengeServiceImpl(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String create(UUID userId, String username, String pendingSecret) {
        Instant now = clock.instant();
        challenges.values().removeIf(open -> open.isExpired(now));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenges.put(id, new Open(new Challenge(id, userId, username, pendingSecret), now.plus(LIFETIME), 0));
        return id;
    }

    @Override
    public Challenge find(String id) {
        if (id == null) {
            return null;
        }
        Open open = challenges.get(id);
        if (open == null || open.isExpired(clock.instant())) {
            challenges.remove(id);
            return null;
        }
        return open.challenge();
    }

    @Override
    public void recordFailure(String id) {
        challenges.computeIfPresent(id, (key, open) ->
            open.failures() + 1 >= MAX_FAILURES ? null : new Open(open.challenge(), open.expiresAt(), open.failures() + 1));
    }

    @Override
    public void remove(String id) {
        if (id != null) {
            challenges.remove(id);
        }
    }

    private record Open(Challenge challenge, Instant expiresAt, int failures) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
