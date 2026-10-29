package com.akhana.akhana_admin.dto;

/** New token after a password change; the ones issued before stop working (T-19). */
public record ChangePasswordResponse(String token) {
}
