package com.akhana.akhana_admin.service;

public interface LoginAttemptService {

    /** Throws LoginBlockedException when the username is locked from this IP, or the IP is locked. */
    void checkNotBlocked(String username, String clientIp);

    /**
     * Counts a failure for the username from this IP and for the IP; throws LoginBlockedException if this failure
     * locks either. A username is locked only from the IP that failed, so nobody can lock out a user from elsewhere.
     */
    void recordFailure(String username, String clientIp);

    void recordSuccess(String username, String clientIp);
}
