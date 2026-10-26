package com.akhana.akhana_admin.dto;

import java.util.UUID;

/** Account that can pay a purchase or its shipping, readable from Compras without Finanzas (COM-05). */
// qrAccount: the QR collections account, the only one besides Caja that takes customer collections (DEU-06)
public record PaymentAccountResponse(UUID id, String name, String color, boolean cashAccount, boolean qrAccount) {}
