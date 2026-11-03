package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ProductPhotoStorageException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Purchase;
import com.akhana.akhana_admin.model.PurchasePhoto;
import com.akhana.akhana_admin.model.PurchaseStatus;
import com.akhana.akhana_admin.repository.PurchasePhotoRepository;
import com.akhana.akhana_admin.repository.PurchaseRepository;
import com.akhana.akhana_admin.service.ProductPhotoService;
import com.akhana.akhana_admin.service.PurchasePhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

// COM-23: reduction, metadata removal, storage and copy off the VPS are those of the product photos (CAT-12, NF-08)
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchasePhotoServiceImpl implements PurchasePhotoService {

    private final PurchasePhotoRepository photoRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductPhotoProcessor processor;
    private final ProductPhotoStorage storage;
    private final TransactionTemplate transactionTemplate;

    @Override
    public UUID upload(UUID purchaseId, byte[] content, String username) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + purchaseId));
        if (purchase.getStatus() == PurchaseStatus.ANULADO || purchase.isHistorical()) {
            throw new IllegalArgumentException(VOIDED_OR_HISTORICAL);
        }

        // Slow work stays out of the transaction, which only registers files that already exist
        ProductPhotoProcessor.ProcessedPhoto processed = processor.process(content);
        UUID photoId = UUID.randomUUID();
        String fileName = photoId + ".jpg";
        String thumbnailFileName = photoId + "-thumb.jpg";
        try {
            storage.store(fileName, processed.image());
            storage.store(thumbnailFileName, processed.thumbnail());
            transactionTemplate.executeWithoutResult(status -> {
                Instant now = Instant.now();
                photoRepository.findActiveForUpdate(purchaseId).ifPresent(current -> {
                    current.setActive(false);
                    current.setDeactivatedBy(username);
                    current.setDeactivatedAt(now);
                    photoRepository.saveAndFlush(current);
                });
                // Flushed in this order because Hibernate runs inserts first: the new row would hit the unique index
                photoRepository.saveAndFlush(PurchasePhoto.builder()
                    .id(photoId)
                    .purchaseId(purchaseId)
                    .fileName(fileName)
                    .thumbnailFileName(thumbnailFileName)
                    .createdBy(username)
                    .createdAt(now)
                    .build());
            });
            return photoId;
        } catch (DataIntegrityViolationException ex) {
            discard(fileName, thumbnailFileName);
            throw new DuplicateResourceException(CONFLICT);
        } catch (IOException | RuntimeException ex) {
            discard(fileName, thumbnailFileName);
            throw new ProductPhotoStorageException(ProductPhotoService.SAVE_FAILED, ex);
        }
    }

    @Override
    public byte[] readImage(UUID purchaseId, UUID photoId) {
        return read(purchaseId, photoId, PurchasePhoto::getFileName);
    }

    @Override
    public byte[] readThumbnail(UUID purchaseId, UUID photoId) {
        return read(purchaseId, photoId, PurchasePhoto::getThumbnailFileName);
    }

    private byte[] read(UUID purchaseId, UUID photoId, Function<PurchasePhoto, String> file) {
        PurchasePhoto photo = photoRepository.findByIdAndPurchaseIdAndActiveTrue(photoId, purchaseId)
            .orElseThrow(PurchasePhotoServiceImpl::photoNotFound);
        try {
            return storage.read(file.apply(photo)).orElseThrow(PurchasePhotoServiceImpl::photoNotFound);
        } catch (IOException ex) {
            log.error("Could not read the purchase photo {}", photoId, ex);
            throw photoNotFound();
        }
    }

    private static ResourceNotFoundException photoNotFound() {
        return new ResourceNotFoundException("Foto no encontrada.");
    }

    private void discard(String... fileNames) {
        for (String fileName : fileNames) {
            try {
                storage.deleteUnregistered(fileName);
            } catch (IOException ex) {
                log.warn("Could not delete the unregistered photo file {}", fileName, ex);
            }
        }
    }
}
