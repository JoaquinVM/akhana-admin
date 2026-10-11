package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoidFinancialTransactionRequest {

    @Size(max = 500, message = "El motivo de anulación no puede exceder 500 caracteres")
    private String voidReason;
}
