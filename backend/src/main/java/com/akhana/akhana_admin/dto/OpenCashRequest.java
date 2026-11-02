package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OpenCashRequest(
    @NotNull(message = "El monto inicial es obligatorio.")
    @DecimalMin(value = "0.00", message = "El monto inicial debe ser mayor o igual a 0.")
    BigDecimal openingAmount,

    @Size(max = 500, message = "El comentario no debe superar los 500 caracteres.")
    String openingComment,

    // CAJ-12: the day of the session; null is today
    LocalDate operationDate,

    // CAJ-13: the days the user saw and confirmed without attention; the server requires exactly the missing ones,
    // so nobody marks a day they did not see
    List<LocalDate> noAttentionDays,

    @Size(max = 500, message = "El comentario de los días sin atención no debe superar los 500 caracteres.")
    String noAttentionComment
) {
    public OpenCashRequest(BigDecimal openingAmount, String openingComment) {
        this(openingAmount, openingComment, null, null, null);
    }
}
