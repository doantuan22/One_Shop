package com.oneshop.exception;

/** Raised when Cloudinary is not configured or an upload/delete call fails. */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message) {
        super(message);
    }

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
