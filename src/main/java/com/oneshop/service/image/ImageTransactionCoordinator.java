package com.oneshop.service.image;

import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.service.CloudinaryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** One lifecycle for Product, Brand and Category assets. No file is deleted before its SQL outcome is known. */
@Component
public class ImageTransactionCoordinator {
    private final CloudinaryService cloudinary;
    private final ImageCleanupService cleanup;

    public ImageTransactionCoordinator(CloudinaryService cloudinary, ImageCleanupService cleanup) {
        this.cloudinary = cloudinary;
        this.cleanup = cleanup;
    }

    public ImageUploadResult upload(MultipartFile file, String folder) {
        requireTransaction();
        ImageUploadResult uploaded = cloudinary.uploadImage(file, folder);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) cleanup.compensate(uploaded.publicId());
                // UNKNOWN is not proof of rollback: never risk deleting a successfully committed asset.
            }
        });
        return uploaded;
    }

    public void deleteAfterCommit(String publicId) {
        if (!StringUtils.hasText(publicId)) return; // local images have no provider public_id
        requireTransaction();
        var task = cleanup.schedule(publicId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cleanup.process(task); }
        });
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Image metadata operations require an active synchronized SQL transaction.");
    }
}
