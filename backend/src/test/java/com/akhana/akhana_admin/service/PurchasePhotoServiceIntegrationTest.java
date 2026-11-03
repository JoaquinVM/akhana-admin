package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.model.Purchase;
import com.akhana.akhana_admin.model.PurchasePhoto;
import com.akhana.akhana_admin.repository.PurchasePhotoRepository;
import com.akhana.akhana_admin.repository.PurchaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// COM-23 against PostgreSQL and real files: the migration, the unique index and the order of the writes
@SpringBootTest
class PurchasePhotoServiceIntegrationTest {

    @TempDir
    static Path photosDir;

    @DynamicPropertySource
    static void photosDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.product-photos.dir", () -> photosDir.toString());
    }

    @Autowired private PurchasePhotoService photoService;
    @Autowired private PurchaseService purchaseService;
    @Autowired private PurchasePhotoRepository photoRepository;
    @Autowired private PurchaseRepository purchaseRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID purchaseId;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        supplierId = UUID.randomUUID();
        purchaseId = UUID.randomUUID();
        jdbc.update("INSERT INTO suppliers (id, name, code, status, created_by, created_at) VALUES (?, ?, ?, 'ACTIVO', 'test', now())",
            supplierId, "Factura " + suffix, "PF" + suffix);
        jdbc.update("""
            INSERT INTO purchases (id, purchase_number, supplier_id, purchase_date, status, items_total, shipping_cost,
                total_amount, paid_amount, consignment, delivery_type, delivery_status, created_by, created_at)
            VALUES (?, ?, ?, CURRENT_DATE, 'REGISTRADO', 10, 0, 10, 0, FALSE, 'EN_TIENDA', 'PENDIENTE', 'test', now())""",
            purchaseId, "PF-" + suffix, supplierId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM purchase_photos WHERE purchase_id = ?", purchaseId);
        jdbc.update("DELETE FROM purchases WHERE id = ?", purchaseId);
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
    @DisplayName("CA-21: la foto queda con el pedido, visible en su detalle y con quién la subió")
    void uploadKeepsThePhotoWithThePurchase() throws IOException {
        UUID photoId = photoService.upload(purchaseId, photo(), "ana");

        PurchasePhoto saved = photoRepository.findById(photoId).orElseThrow();
        assertThat(saved.getCreatedBy()).isEqualTo("ana");
        assertThat(purchaseService.getPurchaseById(purchaseId).photoId()).isEqualTo(photoId);
        assertThat(photoService.readImage(purchaseId, photoId)).isNotEmpty();
        assertThat(photoService.readThumbnail(purchaseId, photoId)).isNotEmpty();
    }

    @Test
    @DisplayName("COM-23: una foto nueva reemplaza a la anterior, que queda inactiva con sus archivos")
    void aNewPhotoReplacesTheOld() throws IOException {
        long before = fileCount();
        UUID first = photoService.upload(purchaseId, photo(), "ana");
        UUID second = photoService.upload(purchaseId, photo(), "beto");

        assertThat(purchaseService.getPurchaseById(purchaseId).photoId()).isEqualTo(second);
        PurchasePhoto old = photoRepository.findById(first).orElseThrow();
        assertThat(old.getActive()).isFalse();
        assertThat(old.getDeactivatedBy()).isEqualTo("beto");
        assertThat(fileCount()).isEqualTo(before + 4);
    }

    @Test
    @DisplayName("CA-23: un archivo inválido no deja nada")
    void anInvalidFileLeavesNothing() throws IOException {
        long before = fileCount();

        assertThatThrownBy(() -> photoService.upload(purchaseId, new byte[] {1, 2, 3}, "ana"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ProductPhotoService.INVALID_FILE);

        assertThat(fileCount()).isEqualTo(before);
        assertThat(photoRepository.findByPurchaseIdAndActiveTrue(purchaseId)).isEmpty();
    }

    @Test
    @DisplayName("COM-23: un pedido anulado no admite fotos")
    void aVoidedPurchaseTakesNoPhoto() {
        jdbc.update("UPDATE purchases SET status = 'ANULADO' WHERE id = ?", purchaseId);

        assertThatThrownBy(() -> photoService.upload(purchaseId, photo(), "ana"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(PurchasePhotoService.VOIDED_OR_HISTORICAL);
    }

    @Test
    @DisplayName("COM-22: la columna nueva arranca sin marca en un pedido existente")
    void existingPurchasesAreNotToConfirm() {
        Purchase purchase = purchaseRepository.findById(purchaseId).orElseThrow();
        assertThat(purchase.getCostToConfirm()).isFalse();
    }
}
