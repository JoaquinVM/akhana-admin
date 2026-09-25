package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.model.User;
import io.jsonwebtoken.Claims;

public interface JwtService {

    String generateToken(User user);

    Claims extractAllClaims(String token);

    String extractUsername(String token);

    String extractRole(String token);

    String extractUserId(String token);

    boolean isTokenValid(String token, String username);

    boolean isTokenExpired(String token);
}
