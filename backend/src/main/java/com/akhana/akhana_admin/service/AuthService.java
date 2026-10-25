package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ChangePasswordRequest;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;

import java.util.UUID;

public interface AuthService {

    LoginResponse login(LoginRequest request, String clientIp);

    void changePassword(UUID userId, ChangePasswordRequest request);
}
