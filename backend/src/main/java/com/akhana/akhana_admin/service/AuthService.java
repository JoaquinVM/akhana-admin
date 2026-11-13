package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ChangePasswordRequest;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.dto.TwoFactorLoginRequest;

import java.util.UUID;

public interface AuthService {

    /** A session, or for an administrator the second factor still to enter (USR-08). */
    LoginResponse login(LoginRequest request, String clientIp);

    /** Finishes an administrator's login with the code of the authenticator app, activating it the first time. */
    LoginResponse loginWithSecondFactor(TwoFactorLoginRequest request, String clientIp);

    /** Changes the password and returns a new token: the ones issued before stop working (T-19). */
    String changePassword(UUID userId, ChangePasswordRequest request);
}
