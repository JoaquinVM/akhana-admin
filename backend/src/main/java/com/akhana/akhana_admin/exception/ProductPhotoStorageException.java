package com.akhana.akhana_admin.exception;

// CAT-12: the photo could not be processed or saved for a reason that is not the file's fault (RG-04)
public class ProductPhotoStorageException extends RuntimeException {

    public ProductPhotoStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
