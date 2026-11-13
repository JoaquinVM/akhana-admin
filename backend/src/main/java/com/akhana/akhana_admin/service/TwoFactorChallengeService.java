package com.akhana.akhana_admin.service;

import java.util.UUID;

/**
 * Administrator logins waiting for the second factor (USR-08). In memory, like the failed login counter: the app is a
 * single instance, and a restart only asks for the password again.
 */
public interface TwoFactorChallengeService {

    /** pendingSecret is the new secret of an administrator activating the second factor; null when it is active. */
    record Challenge(String id, UUID userId, String username, String pendingSecret) {}

    String create(UUID userId, String username, String pendingSecret);

    /** The open challenge, or null when it does not exist, expired or ran out of attempts. */
    Challenge find(String id);

    /** Counts a wrong code; the challenge ends with the last allowed attempt. */
    void recordFailure(String id);

    void remove(String id);
}
