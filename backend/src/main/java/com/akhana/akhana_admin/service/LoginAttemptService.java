package com.akhana.akhana_admin.service;

public interface LoginAttemptService {

    /** Throws LoginBlockedException when the username or the IP is locked. */
    void checkNotBlocked(String username, String clientIp);

    /** Counts a failure for the username and the IP; throws LoginBlockedException if this failure locks either. */
    void recordFailure(String username, String clientIp);

    void recordSuccess(String username);
}
