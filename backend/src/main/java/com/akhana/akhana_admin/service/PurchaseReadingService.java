package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.PurchaseDraftResponse;
import com.akhana.akhana_admin.dto.PurchaseReadingStatusResponse;

public interface PurchaseReadingService {

    String NOT_CONFIGURED = "La lectura con IA no está configurada. Puedes llenar el pedido a mano.";
    String CAP_REACHED = "Se llegó al tope de este mes. Puedes llenar el pedido a mano.";
    String READ_FAILED = "No se pudo leer. Puedes intentar de nuevo o llenarlo a mano.";
    String ONE_SUPPLIER = "Pega un proveedor por vez.";

    PurchaseReadingStatusResponse getStatus(boolean includeSpend);

    PurchaseDraftResponse readText(String text, String username);

    PurchaseDraftResponse readPhoto(byte[] content, String username);
}
