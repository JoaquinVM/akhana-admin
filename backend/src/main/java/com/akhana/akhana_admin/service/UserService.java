package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.CreateUserRequest;
import com.akhana.akhana_admin.dto.UpdateUserRequest;
import com.akhana.akhana_admin.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    Page<UserResponse> getUsers(String term, Pageable pageable);

    UserResponse getUserById(UUID id);

    UserResponse createUser(CreateUserRequest request, String currentUsername);

    UserResponse updateUser(UUID id, UpdateUserRequest request, String currentUsername);

    void deleteUser(UUID id, String currentUsername);
}
