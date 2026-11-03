package com.akhana.akhana_admin.service;

import java.util.UUID;

public interface PurchasePhotoService {

    String CONFLICT = "Otra persona cambió la foto de este pedido. Actualiza la pantalla y vuelve a intentar.";
    String VOIDED_OR_HISTORICAL = "Un pedido anulado o histórico no admite fotos nuevas.";

    /** COM-23: the new photo replaces the one the purchase had; the old row and files are kept. */
    UUID upload(UUID purchaseId, byte[] content, String username);

    byte[] readImage(UUID purchaseId, UUID photoId);

    byte[] readThumbnail(UUID purchaseId, UUID photoId);
}
