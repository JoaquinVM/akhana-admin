package com.akhana.akhana_admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Repayment to a person from Caja or another account (DEU-03): it pays whole the debts that were ticked. The server
 * computes what they owe; {@code amount} is the total the screen showed and must match it. Sales go back as Pago a
 * tercero and purchase payments and shipping as Pedido.
 */
public record CreditorRepaymentRequest(
    @NotNull(message = "La cuenta de origen es obligatoria.")
    UUID accountId,

    @NotNull(message = "El monto es obligatorio.")
    BigDecimal amount,

    @Size(max = 500, message = "El comentario no puede exceder 500 caracteres.")
    String comment,

    @Valid
    List<Item> items
) {

    /** A debt of the person: PAYMENT or SHIPPING (by its id) or SALE (by the sale id), as the history lists it. */
    public record Item(
        @NotNull(message = "Elija las deudas que se pagan.")
        CreditorHistoryResponse.EntryType type,

        @NotNull(message = "Elija las deudas que se pagan.")
        UUID id
    ) {}
}
