package com.akhana.akhana_admin.service;

import java.util.UUID;

public interface ProductPhotoService {

    String INVALID_FILE = "La foto debe ser JPEG, PNG o WebP de hasta 10 MB.";
    String TOO_MANY_PIXELS = "La foto tiene demasiados píxeles.";
    String SAVE_FAILED = "No se pudo guardar la foto. Intenta de nuevo.";
    String CONFLICT = "Otra persona cambió la foto de este producto. Actualiza la pantalla y vuelve a intentar.";
    String DELETED_PRODUCT = "Un producto eliminado no admite fotos nuevas.";

    // expectedPhotoId is the photo the screen was showing, null when it showed none
    UUID upload(UUID productId, byte[] content, UUID expectedPhotoId, String username);

    void remove(UUID productId, UUID expectedPhotoId, String username);

    byte[] readImage(UUID productId, UUID photoId);

    byte[] readThumbnail(UUID productId, UUID photoId);
}
