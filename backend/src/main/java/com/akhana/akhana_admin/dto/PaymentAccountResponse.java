package com.akhana.akhana_admin.dto;

import java.util.UUID;

/** Account that can pay a purchase or its shipping, readable from Compras without Finanzas (COM-05). */
public record PaymentAccountResponse(UUID id, String name, String color, boolean cashAccount) {}
