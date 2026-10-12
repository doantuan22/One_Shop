package com.oneshop.service;

import com.oneshop.dto.response.ImageUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {

    /**
     * Uploads an image to Cloudinary.
     *
     * @param subFolder folder below the configured root folder, for example "products"
     * @return the delivery URL and public id to store in the database
     */
    ImageUploadResult uploadImage(MultipartFile file, String subFolder);

    /** Deletes an image by its Cloudinary public id. Does nothing when the id is blank. */
    void deleteImage(String publicId);

    /**
     * Transforms a Cloudinary delivery URL to insert "f_auto,q_auto,w_<width>" after "/upload/".
     * Returns static paths (starting with "/"), non-Cloudinary URLs, null, or blank strings completely unchanged.
     */
    static String transformUrl(String url, int width) {
        if (url == null || url.isBlank() || url.startsWith("/")) {
            return url;
        }
        if (!url.contains("res.cloudinary.com") || !url.contains("/upload/")) {
            return url;
        }
        if (url.contains("/upload/f_auto") || url.contains("/upload/w_")) {
            return url;
        }
        return url.replaceFirst("/upload/", "/upload/f_auto,q_auto,w_" + width + "/");
    }

    default String optimizeUrl(String url, int width) {
        return transformUrl(url, width);
    }
}
