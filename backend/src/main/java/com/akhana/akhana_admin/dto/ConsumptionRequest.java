package com.akhana.akhana_admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ConsumptionRequest(
    // Null means today (Bolivia time)
    LocalDate consumptionDate,

    @Size(max = 150, message = "El campo «Para quién» no puede exceder 150 caracteres.")
    String consumedFor,

    @Size(max = 500, message = "El comentario no puede exceder 500 caracteres.")
    String comment,

    @NotEmpty(message = "El consumo debe tener al menos una línea.")
    List<@Valid ConsumptionItemRequest> items
) {}
