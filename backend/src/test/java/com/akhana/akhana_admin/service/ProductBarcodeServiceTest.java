package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ProductBarcodeRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.impl.ProductBarcodeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CAT-10 and the lookup of POS-15. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductBarcodeServiceTest {

    private static final String EAN = "7791234567898";
    private static final String OTHER_EAN = "4006381333931";

    @Mock private ProductBarcodeRepository barcodeRepository;
    @Mock private ProductRepository productRepository;

    private ProductBarcodeServiceImpl service;
    private Product shampoo;

    @BeforeEach
    void setUp() {
        service = new ProductBarcodeServiceImpl(barcodeRepository, productRepository);
        shampoo = product("NEF-SHA", "Shampoo", SaleUnit.UNIT);
        when(productRepository.findByIdWithRelations(shampoo.getId())).thenReturn(Optional.of(shampoo));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(barcodeRepository.findActiveByBarcode(any())).thenReturn(Optional.empty());
    }

    private static Product product(String code, String name, SaleUnit unit) {
        return Product.builder().id(UUID.randomUUID()).code(code).name(name).saleUnit(unit)
            .buyPrice(new BigDecimal("10.00")).sellPrice(new BigDecimal("20.00")).status(ProductStatus.ACTIVO)
            .barcodes(new ArrayList<>()).build();
    }

    private static ProductBarcode existing(Product product, String code) {
        ProductBarcode barcode = ProductBarcode.builder().id(UUID.randomUUID()).product(product).barcode(code)
            .createdBy("admin").createdAt(Instant.now()).build();
        product.getBarcodes().add(barcode);
        return barcode;
    }

    @Test
    void addsTheCanonicalCodeWithWhoAndWhen() {
        ProductResponse response = service.addBarcode(shampoo.getId(), "036000291452", "operador");

        assertThat(response.barcodes()).containsExactly("0036000291452");
        ProductBarcode saved = shampoo.getBarcodes().getFirst();
        assertThat(saved.getCreatedBy()).isEqualTo("operador");
        assertThat(saved.getCreatedAt()).isNotNull();
        verify(barcodeRepository).saveAndFlush(saved);
    }

    @Test
    void rejectsAnInvalidCode() {
        assertThatThrownBy(() -> service.addBarcode(shampoo.getId(), "7791234567890", "operador"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("El código de barras no es válido.");
    }

    @Test
    void rejectsACodeOfAnotherProductNamingIt() {
        Product other = product("NEF-CRE", "Crema", SaleUnit.UNIT);
        other.setStatus(ProductStatus.INACTIVO);
        when(barcodeRepository.findActiveByBarcode(EAN)).thenReturn(Optional.of(existing(other, EAN)));

        assertThatThrownBy(() -> service.addBarcode(shampoo.getId(), EAN, "operador"))
            .isInstanceOf(DuplicateResourceException.class).hasMessage("El código ya es de Crema (NEF-CRE).");
        assertThat(shampoo.getBarcodes()).isEmpty();
    }

    @Test
    void aRaceAgainstTheUniqueIndexIsADuplicate() {
        when(barcodeRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.addBarcode(shampoo.getId(), EAN, "operador"))
            .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void addingTheCodeItAlreadyHasChangesNothing() {
        existing(shampoo, EAN);

        assertThat(service.addBarcode(shampoo.getId(), EAN, "operador").barcodes()).containsExactly(EAN);
        verify(barcodeRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsASixthCode() {
        for (String code : List.of("96385074", "0036000291452", "4006381333931", "5901234123457", EAN)) {
            existing(shampoo, code);
        }

        assertThatThrownBy(() -> service.addBarcode(shampoo.getId(), "8410076472038", "operador"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Un producto admite hasta 5 códigos de barras.");
    }

    @Test
    void kiloAndSpecialProductsDoNotAdmitCodes() {
        Product nut = product("FSE-NUE", "Nuez", SaleUnit.KILOGRAM);
        Product special = product("SIN-IDENT", "Sin identificar", SaleUnit.UNIT);
        special.setSpecialType(ProductSpecialType.UNIDENTIFIED);

        for (Product product : List.of(nut, special)) {
            assertThatThrownBy(() -> service.applyBarcodes(product, List.of(EAN), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Este producto no admite código de barras.");
        }
    }

    @Test
    void aProductWithCodesDoesNotMoveToKilo() {
        existing(shampoo, EAN);
        shampoo.setSaleUnit(SaleUnit.KILOGRAM);

        assertThatThrownBy(() -> service.applyBarcodes(shampoo, null, "admin"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Quita sus códigos de barras para venderlo por kilo.");
    }

    @Test
    void removingItsCodesLetsItMoveToKilo() {
        ProductBarcode barcode = existing(shampoo, EAN);
        shampoo.setSaleUnit(SaleUnit.KILOGRAM);

        service.applyBarcodes(shampoo, List.of(), "admin");

        assertThat(barcode.isActive()).isFalse();
    }

    @Test
    void removingACodeDeactivatesItWithWhoAndWhen() {
        ProductBarcode removed = existing(shampoo, EAN);
        ProductBarcode kept = existing(shampoo, OTHER_EAN);

        service.applyBarcodes(shampoo, List.of(OTHER_EAN), "admin");

        assertThat(removed.isActive()).isFalse();
        assertThat(removed.getDeactivatedBy()).isEqualTo("admin");
        assertThat(removed.getDeactivatedAt()).isNotNull();
        assertThat(kept.isActive()).isTrue();
        verify(barcodeRepository, never()).delete(any());
    }

    @Test
    void aNullListKeepsTheCodes() {
        ProductBarcode barcode = existing(shampoo, EAN);

        service.applyBarcodes(shampoo, null, "admin");

        assertThat(barcode.isActive()).isTrue();
    }

    @Test
    void theSameCodeWrittenAsUpcAndEanCountsOnce() {
        service.applyBarcodes(shampoo, List.of("036000291452", "0036000291452"), "admin");

        assertThat(shampoo.getBarcodes()).hasSize(1);
    }

    @Test
    void marksNoBarcodeWithWhoAndWhen() {
        ProductResponse response = service.markNoBarcode(shampoo.getId(), "operador");

        assertThat(response.noBarcode()).isTrue();
        assertThat(shampoo.getNoBarcodeMarkedBy()).isEqualTo("operador");
        assertThat(shampoo.getNoBarcodeMarkedAt()).isNotNull();
    }

    @Test
    void doesNotMarkAProductThatHasACode() {
        existing(shampoo, EAN);

        assertThatThrownBy(() -> service.markNoBarcode(shampoo.getId(), "operador"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Este producto ya tiene código de barras.");
    }

    @Test
    void receivingACodeClearsTheMark() {
        service.markNoBarcode(shampoo.getId(), "operador");

        assertThat(service.addBarcode(shampoo.getId(), EAN, "operador").noBarcode()).isFalse();
        assertThat(shampoo.getNoBarcodeMarkedBy()).isNull();
    }

    @Test
    void theMarkCanBeClearedByHand() {
        service.markNoBarcode(shampoo.getId(), "operador");

        assertThat(service.clearNoBarcode(shampoo.getId(), "admin").noBarcode()).isFalse();
    }

    @Test
    void deactivateAllFreesTheCodes() {
        ProductBarcode barcode = existing(shampoo, EAN);

        service.deactivateAll(shampoo, "admin");

        assertThat(barcode.isActive()).isFalse();
    }

    @Test
    void findsAProductByCodeInEitherForm() {
        existing(shampoo, "0036000291452");
        when(barcodeRepository.findActiveByBarcode("0036000291452"))
            .thenReturn(Optional.of(shampoo.getBarcodes().getFirst()));

        assertThat(service.findByBarcode("036000291452").id()).isEqualTo(shampoo.getId());
        assertThat(service.findByBarcode("0036000291452").id()).isEqualTo(shampoo.getId());
    }

    @Test
    void anUnknownOrInvalidCodeIsNotFound() {
        assertThatThrownBy(() -> service.findByBarcode(EAN)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.findByBarcode("123")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aDeletedProductCannotReceiveACode() {
        shampoo.setStatus(ProductStatus.ELIMINADO);

        assertThatThrownBy(() -> service.addBarcode(shampoo.getId(), EAN, "operador"))
            .isInstanceOf(IllegalStateException.class);
    }
}
