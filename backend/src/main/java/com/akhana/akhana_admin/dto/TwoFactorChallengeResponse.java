package com.akhana.akhana_admin.dto;

/**
 * The second factor an administrator must enter to finish the login (USR-08). secret and otpauthUri go only while
 * activating it (setupRequired), for the QR code; once active they are never sent again.
 */
public record TwoFactorChallengeResponse(
    String challengeId,
    boolean setupRequired,
    String secret,
    String otpauthUri
) {}
