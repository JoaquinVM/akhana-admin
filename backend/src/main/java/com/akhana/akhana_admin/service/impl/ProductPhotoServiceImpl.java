package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ProductPhotoStorageException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductPhoto;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.repository.ProductPhotoRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.ProductPhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPhotoServiceImpl implements ProductPhotoService {

    private final ProductPhotoRepository photoRepository;
    private final ProductRepository productRepository;
    private final ProductPhotoProcessor processor;
    private final ProductPhotoStorage storage;
    private final TransactionTemplate transactionTemplate;

    @Override
    public UUID upload(UUID productId, byte[] content, UUID expectedPhotoId, String username) {
        Product product = findProduct(productId);
        if (product.getStatus() == ProductStatus.ELIMINADO) {
            throw new IllegalArgumentException(DELETED_PRODUCT);
        }
        ensureExpected(productId, expectedPhotoId);

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
                ProductPhoto current = findExpected(productId, expectedPhotoId);
                if (current != null) {
                    deactivate(current, username, now);
                }
                // Flushed in this order because Hibernate runs inserts first: the new row would hit the unique index
                photoRepository.saveAndFlush(ProductPhoto.builder()
                    .id(photoId)
                    .productId(productId)
                    .fileName(fileName)
                    .thumbnailFileName(thumbnailFileName)
                    .createdBy(username)
                    .createdAt(now)
                    .build());
            });
            return photoId;
        } catch (DuplicateResourceException ex) {
            discard(fileName, thumbnailFileName);
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            discard(fileName, thumbnailFileName);
            throw new DuplicateResourceException(CONFLICT);
        } catch (IOException | RuntimeException ex) {
            discard(fileName, thumbnailFileName);
            throw new ProductPhotoStorageException(SAVE_FAILED, ex);
        }
    }

    @Override
    public void remove(UUID productId, UUID expectedPhotoId, String username) {
        findProduct(productId);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ProductPhoto current = findExpected(productId, expectedPhotoId);
                if (current == null) {
                    throw new DuplicateResourceException(CONFLICT);
                }
                deactivate(current, username, Instant.now());
            });
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(CONFLICT);
        }
    }

    @Override
    public byte[] readImage(UUID productId, UUID photoId) {
        return read(productId, photoId, ProductPhoto::getFileName);
    }

    @Override
    public byte[] readThumbnail(UUID productId, UUID photoId) {
        return read(productId, photoId, ProductPhoto::getThumbnailFileName);
    }

    private byte[] read(UUID productId, UUID photoId, Function<ProductPhoto, String> file) {
        ProductPhoto photo = photoRepository.findByIdAndProductIdAndActiveTrue(photoId, productId)
            .orElseThrow(ProductPhotoServiceImpl::photoNotFound);
        try {
            return storage.read(file.apply(photo)).orElseThrow(ProductPhotoServiceImpl::photoNotFound);
        } catch (IOException ex) {
            log.error("Could not read the photo {}", photoId, ex);
            throw photoNotFound();
        }
    }

    private static ResourceNotFoundException photoNotFound() {
        return new ResourceNotFoundException("Foto no encontrada.");
    }

    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
    }

    private void ensureExpected(UUID productId, UUID expectedPhotoId) {
        UUID currentId = photoRepository.findByProductIdAndActiveTrue(productId).map(ProductPhoto::getId).orElse(null);
        if (!Objects.equals(currentId, expectedPhotoId)) {
            throw new DuplicateResourceException(CONFLICT);
        }
    }

    // A stale screen gets a conflict; the unique index covers the saves that are truly simultaneous
    private ProductPhoto findExpected(UUID productId, UUID expectedPhotoId) {
        ProductPhoto current = photoRepository.findActiveForUpdate(productId).orElse(null);
        UUID currentId = current != null ? current.getId() : null;
        if (!Objects.equals(currentId, expectedPhotoId)) {
            throw new DuplicateResourceException(CONFLICT);
        }
        return current;
    }

    private void deactivate(ProductPhoto photo, String username, Instant now) {
        photo.setActive(false);
        photo.setDeactivatedBy(username);
        photo.setDeactivatedAt(now);
        photoRepository.saveAndFlush(photo);
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
