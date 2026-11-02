package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ProductPhotoStorageException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.ProductPhoto;
import com.akhana.akhana_admin.repository.ProductPhotoRepository;
import com.akhana.akhana_admin.service.impl.ProductPhotoStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// CAT-12 against PostgreSQL and real files: the unique index and the order of the writes are what is being tested
@SpringBootTest
class ProductPhotoServiceIntegrationTest {

    @TempDir
    static Path photosDir;

    @DynamicPropertySource
    static void photosDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.product-photos.dir", () -> photosDir.toString());
    }

    @Autowired private ProductPhotoService photoService;
    @Autowired private ProductService productService;
    @Autowired private ProductPhotoRepository photoRepository;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ProductPhotoStorage storage;

    private UUID productId;
    private UUID categoryId;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        categoryId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        productId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, color, status, created_by, created_at) VALUES (?, ?, '#111111', 'ACTIVO', 'test', now())",
            categoryId, "Foto " + suffix);
        jdbc.update("INSERT INTO suppliers (id, name, code, status, created_by, created_at) VALUES (?, ?, ?, 'ACTIVO', 'test', now())",
            supplierId, "Foto " + suffix, "F" + suffix);
        jdbc.update("""
            INSERT INTO products (id, code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit,
                percentage_profit, status, sale_unit, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, 1, 2, 1, 100, 'ACTIVO', 'UNIT', 'test', now())""",
            productId, "F-" + suffix, "Foto " + suffix, categoryId, supplierId);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(storage);
        jdbc.update("DELETE FROM product_photos WHERE product_id = ?", productId);
        jdbc.update("DELETE FROM products WHERE id = ?", productId);
        jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
        jdbc.update("DELETE FROM suppliers WHERE id = ?", supplierId);
    }

    private static byte[] photo() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        return bytes.toByteArray();
    }

    private long fileCount() throws IOException {
        try (Stream<Path> files = Files.list(photosDir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    @Test
    @DisplayName("Subir guarda dos archivos, audita y el catálogo trae solo la referencia")
    void upload_savesFilesAndReference() throws IOException {
        UUID photoId = photoService.upload(productId, photo(), null, "ana");

        ProductPhoto saved = photoRepository.findById(photoId).orElseThrow();
        assertThat(saved.getCreatedBy()).isEqualTo("ana");
        assertThat(saved.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(saved.getFileName()).isEqualTo(photoId + ".jpg");
        assertThat(Files.exists(photosDir.resolve(saved.getFileName()))).isTrue();
        assertThat(Files.exists(photosDir.resolve(saved.getThumbnailFileName()))).isTrue();
        assertThat(photoService.readImage(productId, photoId)).isNotEmpty();
        assertThat(photoService.readThumbnail(productId, photoId)).isNotEmpty();
        assertThat(productService.getProductById(productId).photoId()).isEqualTo(photoId);
        ProductResponse inCatalog = productService.getAllProducts(null, null).stream()
            .filter(p -> p.id().equals(productId)).findFirst().orElseThrow();
        assertThat(inCatalog.photoId()).isEqualTo(photoId);
    }

    @Test
    @DisplayName("Cambiar deja la anterior inactiva, con su archivo y quién la cambió")
    void replace_keepsPreviousFile() throws IOException {
        UUID first = photoService.upload(productId, photo(), null, "ana");

        UUID second = photoService.upload(productId, photo(), first, "luis");

        ProductPhoto previous = photoRepository.findById(first).orElseThrow();
        assertThat(previous.getActive()).isFalse();
        assertThat(previous.getDeactivatedBy()).isEqualTo("luis");
        assertThat(previous.getDeactivatedAt()).isNotNull();
        assertThat(Files.exists(photosDir.resolve(previous.getFileName()))).isTrue();
        assertThat(productService.getProductById(productId).photoId()).isEqualTo(second);
        assertThatThrownBy(() -> photoService.readImage(productId, first)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Quitar deja el producto sin foto y conserva el archivo")
    void remove_keepsFile() throws IOException {
        UUID photoId = photoService.upload(productId, photo(), null, "ana");

        photoService.remove(productId, photoId, "luis");

        ProductPhoto removed = photoRepository.findById(photoId).orElseThrow();
        assertThat(removed.getActive()).isFalse();
        assertThat(removed.getDeactivatedBy()).isEqualTo("luis");
        assertThat(Files.exists(photosDir.resolve(removed.getFileName()))).isTrue();
        assertThat(productService.getProductById(productId).photoId()).isNull();
    }

    @Test
    @DisplayName("Inactivar o eliminar el producto no cambia su foto; eliminado no admite fotos nuevas")
    void productStatus_doesNotTouchPhoto() throws IOException {
        UUID photoId = photoService.upload(productId, photo(), null, "ana");

        jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?", productId);
        assertThat(productService.getProductById(productId).photoId()).isEqualTo(photoId);
        jdbc.update("UPDATE products SET status = 'ELIMINADO' WHERE id = ?", productId);
        assertThat(productService.getProductById(productId).photoId()).isEqualTo(photoId);

        assertThatThrownBy(() -> photoService.upload(productId, photo(), photoId, "ana"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ProductPhotoService.DELETED_PRODUCT);
    }

    @Test
    @DisplayName("Una pantalla vieja recibe un conflicto al subir y al quitar")
    void staleScreen_getsConflict() throws IOException {
        UUID current = photoService.upload(productId, photo(), null, "ana");
        byte[] content = photo();

        assertThatThrownBy(() -> photoService.upload(productId, content, null, "luis"))
            .isInstanceOf(DuplicateResourceException.class).hasMessage(ProductPhotoService.CONFLICT);
        assertThatThrownBy(() -> photoService.remove(productId, UUID.randomUUID(), "luis"))
            .isInstanceOf(DuplicateResourceException.class).hasMessage(ProductPhotoService.CONFLICT);
        assertThat(productService.getProductById(productId).photoId()).isEqualTo(current);
    }

    @Test
    @DisplayName("Varias subidas a la vez: una se guarda, las otras reciben conflicto y no dejan archivos")
    void simultaneousUploads_oneWins() throws Exception {
        byte[] content = photo();
        long filesBefore = fileCount();
        CountDownLatch start = new CountDownLatch(1);
        Callable<UUID> upload = () -> {
            start.await();
            return photoService.upload(productId, content, null, "ana");
        };
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(upload));
        }
        start.countDown();

        int saved = 0;
        int conflicts = 0;
        for (Future<UUID> future : futures) {
            try {
                future.get();
                saved++;
            } catch (ExecutionException ex) {
                assertThat(ex.getCause()).isInstanceOf(DuplicateResourceException.class);
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(saved).isEqualTo(1);
        assertThat(conflicts).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_photos WHERE product_id = ? AND active", Long.class, productId)).isEqualTo(1);
        assertThat(fileCount()).isEqualTo(filesBefore + 2);
    }

    @Test
    @DisplayName("La base no admite dos fotos activas del mismo producto")
    void uniqueIndex_rejectsSecondActive() throws IOException {
        photoService.upload(productId, photo(), null, "ana");

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO product_photos (id, product_id, file_name, thumbnail_file_name, created_by, created_at)
            VALUES (?, ?, ?, ?, 'x', now())""", UUID.randomUUID(), productId, "a" + productId, "b" + productId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Si falla el guardado, conserva la foto anterior, no deja archivos y no muestra detalles")
    void storageFailure_keepsPreviousPhoto() throws IOException {
        UUID current = photoService.upload(productId, photo(), null, "ana");
        long filesBefore = fileCount();
        byte[] content = photo();
        Mockito.doCallRealMethod().doThrow(new IOException("No space left on /data/product-photos"))
            .when(storage).store(Mockito.anyString(), Mockito.any());

        assertThatThrownBy(() -> photoService.upload(productId, content, current, "ana"))
            .isInstanceOf(ProductPhotoStorageException.class)
            .hasMessage(ProductPhotoService.SAVE_FAILED);

        assertThat(productService.getProductById(productId).photoId()).isEqualTo(current);
        assertThat(fileCount()).isEqualTo(filesBefore);
    }

    @Test
    @DisplayName("Si falta el archivo responde no encontrado, para que la pantalla marque «Sin foto»")
    void missingFile_isNotFound() throws IOException {
        UUID photoId = photoService.upload(productId, photo(), null, "ana");
        Files.delete(photosDir.resolve(photoId + "-thumb.jpg"));

        assertThatThrownBy(() -> photoService.readThumbnail(productId, photoId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
