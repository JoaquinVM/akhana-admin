package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record QuickProductGroupRequest(
    @NotBlank(message = "El nombre del grupo es obligatorio")
    @Size(max = 100, message = "El nombre del grupo no puede superar los 100 caracteres")
    String name,

    List<UUID> productIds
) {}
