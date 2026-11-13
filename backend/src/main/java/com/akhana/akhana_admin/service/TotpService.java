package com.akhana.akhana_admin.service;

import java.util.OptionalLong;

/** One-time codes of the administrators' second factor (USR-08): RFC 6238 with SHA-1, 6 digits and 30 seconds. */
public interface TotpService {

    /** A new random secret, in Base32 as the authenticator apps read it. */
    String generateSecret();

    /**
     * The time step of the code when it is valid now, with one step of clock drift either way, and later than
     * lastUsedStep, so the same code is never accepted twice. Empty otherwise.
     */
    OptionalLong matchingStep(String secret, String code, Long lastUsedStep);

    /** The otpauth:// address that the QR code carries to the authenticator app. */
    String otpauthUri(String secret, String username);
}
