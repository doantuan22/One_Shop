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
}
