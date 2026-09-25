package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
