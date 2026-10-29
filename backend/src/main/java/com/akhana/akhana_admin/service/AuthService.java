package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ChangePasswordRequest;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;

import java.util.UUID;

public interface AuthService {

    LoginResponse login(LoginRequest request, String clientIp);

    /** Changes the password and returns a new token: the ones issued before stop working (T-19). */
    String changePassword(UUID userId, ChangePasswordRequest request);
}
