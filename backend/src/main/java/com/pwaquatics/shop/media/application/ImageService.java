package com.pwaquatics.shop.media.application;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.ids.Ids;
import com.pwaquatics.shop.media.infrastructure.MediaStorage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Stores and deletes uploaded images for other modules. Files follow the caller's
 * transaction: stored files are removed again if it rolls back, and deleted files are only
 * removed once it has committed.
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    private final ImageProcessor processor;
    private final MediaStorage storage;

    ImageService(ImageProcessor processor, MediaStorage storage) {
        this.processor = processor;
        this.storage = storage;
    }

    /**
     * Processes a product photo (see {@link ImageProcessor}) and stores its WebP renditions.
     *
     * @throws BusinessRuleException if the file is not a usable photo
     */
    public StoredImage storeProductImage(UUID productId, byte[] bytes) {
        ImageProcessor.Result result = processor.process(bytes);
        String key = "products/" + productId + "/" + Ids.newId();
        storage.store(key, result.renditions());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteQuietly(key);
                    }
                }
            });
        }
        return new StoredImage(key, result.width(), result.height());
    }

    /** Removes an image's files once the surrounding transaction has committed. */
    public void deleteAfterCommit(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(storageKey);
                }
            });
        } else {
            deleteQuietly(storageKey);
        }
    }

    private void deleteQuietly(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException e) {
            // The database no longer references the files; a leftover directory is harmless.
            log.warn("Could not delete image files {}: {}", storageKey, e.getClass().getSimpleName());
        }
    }
}
