package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.WebCatalog;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductPhoto;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.repository.ProductPhotoRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.WebCatalogExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The folder is only a copy for the public site: the host uploads it (deploy/publish-web-catalog.sh) and nothing here
 * touches the photos of Admin. Photos are written before the JSON that names them and removed after it, so a reader
 * never finds a product pointing to a missing file.
 */
@Slf4j
@Service
public class WebCatalogExportServiceImpl implements WebCatalogExportService {

    static final String CATALOG_FILE = "catalog.json";
    static final String PHOTOS_DIR = "photos";

    private final ProductRepository productRepository;
    private final ProductPhotoRepository photoRepository;
    private final ProductPhotoStorage storage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readOnly;
    private final Path directory;

    public WebCatalogExportServiceImpl(ProductRepository productRepository, ProductPhotoRepository photoRepository,
                                       ProductPhotoStorage storage, ObjectMapper objectMapper,
                                       PlatformTransactionManager transactionManager,
                                       @Value("${app.web-catalog.dir}") String directory) {
        this.productRepository = productRepository;
        this.photoRepository = photoRepository;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    private record Candidate(WebCatalog.Item item, ProductPhoto photo) {}

    @Override
    public void export() throws IOException {
        // Reads inside a transaction and writes outside it, so slow files do not hold a connection the POS shares
        List<Candidate> candidates = readOnly.execute(status -> readCandidates());

        Path photos = directory.resolve(PHOTOS_DIR);
        Files.createDirectories(photos);

        List<WebCatalog.Item> items = new ArrayList<>();
        Set<String> keptFiles = new HashSet<>();
        for (Candidate candidate : candidates) {
            String image = candidate.photo().getId() + ".jpg";
            String thumbnail = candidate.photo().getId() + "-thumb.jpg";
            // CAT-12: the row can exist with its file lost; without the file the product does not go out
            if (copyPhoto(candidate.photo().getFileName(), photos.resolve(image))
                && copyPhoto(candidate.photo().getThumbnailFileName(), photos.resolve(thumbnail))) {
                items.add(candidate.item());
                keptFiles.add(image);
                keptFiles.add(thumbnail);
            } else {
                log.warn("Catálogo web: falta el archivo de la foto {}", candidate.photo().getId());
            }
        }

        writeCatalog(objectMapper.writeValueAsBytes(new WebCatalog(items)));
        removeOthers(photos, keptFiles);
    }

    private List<Candidate> readCandidates() {
        List<Product> products = productRepository.findWebPublishable(ProductStatus.ACTIVO).stream()
            .filter(product -> product.getActivePhotoId() != null)
            .toList();
        Map<UUID, ProductPhoto> photos = photoRepository
            .findAllById(products.stream().map(Product::getActivePhotoId).toList()).stream()
            .collect(Collectors.toMap(ProductPhoto::getId, Function.identity()));
        return products.stream()
            .filter(product -> photos.containsKey(product.getActivePhotoId()))
            .map(product -> new Candidate(toItem(product), photos.get(product.getActivePhotoId())))
            .toList();
    }

    private static WebCatalog.Item toItem(Product product) {
        List<WebCatalog.Presentation> presentations = product.getPresentations().stream()
            .filter(presentation -> presentation.isActive())
            .map(presentation -> new WebCatalog.Presentation(presentation.getName(),
                money(ProductPresentationServiceImpl.priceOf(presentation, product))))
            .toList();
        return new WebCatalog.Item(
            product.getCode(),
            product.getName(),
            product.getCategory().getName(),
            product.getDescription(),
            product.getSaleUnit(),
            money(product.getSellPrice()),
            product.getActivePhotoId(),
            presentations
        );
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // A saved photo never changes under its id, so one already copied is left as it is
    private boolean copyPhoto(String source, Path target) throws IOException {
        if (Files.exists(target)) {
            return true;
        }
        Optional<byte[]> content = storage.read(source);
        if (content.isEmpty()) {
            return false;
        }
        writeAtomically(target, content.get());
        return true;
    }

    // Rewrites only a different catalog; the new time says the export is alive even when nothing changed
    private void writeCatalog(byte[] content) throws IOException {
        Path catalog = directory.resolve(CATALOG_FILE);
        byte[] current = null;
        try {
            current = Files.readAllBytes(catalog);
        } catch (NoSuchFileException ex) {
            log.info("Catálogo web: primera exportación");
        }
        if (Arrays.equals(current, content)) {
            Files.setLastModifiedTime(catalog, FileTime.from(Instant.now()));
        } else {
            writeAtomically(catalog, content);
        }
    }

    private void writeAtomically(Path target, byte[] content) throws IOException {
        Path partial = Files.createTempFile(directory, ".partial-", null);
        try {
            Files.write(partial, content);
            // The upload runs as another user and must be able to read the file
            Files.setPosixFilePermissions(partial, PosixFilePermissions.fromString("rw-r--r--"));
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private void removeOthers(Path photos, Set<String> keptFiles) throws IOException {
        List<Path> others;
        try (Stream<Path> files = Files.list(photos)) {
            others = files.filter(file -> !keptFiles.contains(file.getFileName().toString())).toList();
        }
        for (Path other : others) {
            Files.deleteIfExists(other);
        }
    }
}
