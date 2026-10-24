package com.oneshop.service.image;

import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.ImageCleanupRepository;
import com.oneshop.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Idempotent cleanup. A provider failure never turns a committed metadata change into a failed request. */
@Service
public class ImageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);
    private final ImageCleanupRepository tasks;
    private final CloudinaryService cloudinary;
    private final TransactionTemplate independent;

    public ImageCleanupService(ImageCleanupRepository tasks, CloudinaryService cloudinary, PlatformTransactionManager manager) {
        this.tasks = tasks;
        this.cloudinary = cloudinary;
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Same transaction as removal of the metadata: durable even if the process dies just after commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID schedule(String publicId) { return tasks.enqueue(publicId); }

    /** Called only after rollback, when an independent transaction can safely inspect committed references. */
    public void compensate(String publicId) {
        try {
            UUID id = independent.execute(s -> tasks.enqueue(publicId));
            process(id);
        } catch (RuntimeException ex) {
            log.error("IMAGE_CLEANUP_INTENT_FAILED public_id={} cause={}; reconcile provider assets manually",
                    publicId, ex.getClass().getSimpleName());
        }
    }

    public void process(UUID id) {
        try {
            independent.executeWithoutResult(s -> {
                var task = tasks.lock(id);
                if (task == null) return; // already processed, including a concurrent retry
                if (tasks.isReferenced(task.publicId())) {
                    // Another valid metadata row owns the file; its eventual removal schedules its own task.
                    tasks.complete(id);
                    return;
                }
                try {
                    cloudinary.deleteImage(task.publicId());
                    tasks.complete(id);
                } catch (RuntimeException ex) {
                    String reason = ex.getClass().getSimpleName() + ": " + safeReason(ex.getMessage());
                    tasks.failed(id, reason);
                    log.warn("IMAGE_CLEANUP_PENDING task={} public_id={} cause={}", id, task.publicId(), reason);
                }
            });
        } catch (RuntimeException ex) {
            log.error("IMAGE_CLEANUP_RETRY_REQUIRED task={} cause={}", id, ex.getClass().getSimpleName());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public int retryPending(int limit) {
        if (limit < 1 || limit > 200) throw new BadRequestException("limit", "Số tác vụ cần từ 1 đến 200.");
        var ids = tasks.pending(limit);
        ids.forEach(this::process);
        return ids.size();
    }

    private static String safeReason(String text) {
        if (text == null) return "external cleanup failed";
        // Provider errors must never persist credentials/query strings in the operational journal.
        return text.replaceAll("(?i)(api[_-]?(?:key|secret)|password|signature|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("https?://[^\\s]+", "[provider URL omitted]");
    }
}
