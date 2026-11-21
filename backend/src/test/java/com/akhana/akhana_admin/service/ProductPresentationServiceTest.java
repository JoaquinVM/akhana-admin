package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.dto.ProductPresentationResponse;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ProductPresentationRepository;
import com.akhana.akhana_admin.repository.ProductPriceChangeRepository;
import com.akhana.akhana_admin.service.impl.ProductPresentationServiceImpl;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CAT-15 (and the presentations of CAT-11) with the examples of specs/inventory.md and product-presentations.md: Nuez
 * pelada by kilo (cost 130,00 and price 210,00 per kilo) and Barra Chia by unit (cost 2,13 and price 4,00).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductPresentationServiceTest {

    @Mock private ProductPresentationRepository presentationRepository;
    @Mock private ProductPriceChangeRepository priceChangeRepository;
    @Mock private ProductBarcodeService barcodeService;

    private ProductPresentationServiceImpl service;
    private Product nut;
    private Product bar;

    @BeforeEach
    void setUp() {
        service = new ProductPresentationServiceImpl(presentationRepository, priceChangeRepository, barcodeService);
        nut = Product.builder().id(UUID.randomUUID()).code("FSE-NUE").name("Nuez pelada").saleUnit(SaleUnit.KILOGRAM)
            .buyPrice(new BigDecimal("130.00")).sellPrice(new BigDecimal("210.00")).status(ProductStatus.ACTIVO)
            .presentations(new ArrayList<>()).build();
        bar = Product.builder().id(UUID.randomUUID()).code("NSE-BAR-CHI").name("Barra Chia").saleUnit(SaleUnit.UNIT)
            .buyPrice(new BigDecimal("2.13")).sellPrice(new BigDecimal("4.00")).status(ProductStatus.ACTIVO)
            .presentations(new ArrayList<>()).build();
        when(presentationRepository.save(any(ProductPresentation.class))).thenAnswer(inv -> {
            ProductPresentation p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    private static ProductPresentationRequest request(String name, String quantity, String fixedPrice) {
        return new ProductPresentationRequest(null, name, quantity != null ? new BigDecimal(quantity) : null,
            fixedPrice != null ? new BigDecimal(fixedPrice) : null, true);
    }

    private static ProductPresentationRequest request(String name, String quantity, String fixedPrice, boolean sold,
                                                      String barcode) {
        return new ProductPresentationRequest(null, name, new BigDecimal(quantity),
            fixedPrice != null ? new BigDecimal(fixedPrice) : null, true, sold, barcode);
    }

    private ProductPresentation existing(Product product, String name, String quantity, String fixedPrice) {
        ProductPresentation presentation = ProductPresentation.builder().id(UUID.randomUUID()).product(product).name(name)
            .baseQuantity(new BigDecimal(quantity)).fixedPrice(fixedPrice != null ? new BigDecimal(fixedPrice) : null)
            .active(true).createdBy("admin").build();
        product.getPresentations().add(presentation);
        return presentation;
    }

    private ProductPresentation existing(String name, String weight, String fixedPrice) {
        return existing(nut, name, weight, fixedPrice);
    }

    private static ProductPresentationRequest keep(ProductPresentation p, String fixedPrice) {
        return new ProductPresentationRequest(p.getId(), p.getName(), p.getBaseQuantity(),
            fixedPrice != null ? new BigDecimal(fixedPrice) : null, true);
    }

    private List<ProductPresentationResponse> responses(Product product) {
        return product.getPresentations().stream().map(p -> ProductPresentationResponse.fromEntity(p, product)).toList();
    }

    @Test
    @DisplayName("Por kilo: crea los empaques con precio propio o calculado y costo siempre calculado, de menor a mayor")
    void createsPresentationsWithPriceAndCost() {
        List<String> warnings = service.applyPresentations(nut, List.of(
            request("1 libra", "0.454", "90.00"),
            request("100 g", "0.100", null),
            request(" 1/2 libra ", "0.227", "45")), null, null, "admin");

        assertThat(warnings).isEmpty();
        List<ProductPresentationResponse> responses = responses(nut);
        assertThat(responses).extracting(ProductPresentationResponse::name).containsExactly("100 g", "1/2 libra", "1 libra");
        assertThat(responses).extracting(r -> r.price().toPlainString()).containsExactly("21.00", "45.00", "90.00");
        assertThat(responses).extracting(r -> r.cost().toPlainString()).containsExactly("13.00", "29.51", "59.02");
        // The presentations of CAT-11 are packages that are sold
        assertThat(responses).allMatch(ProductPresentationResponse::sold);
        assertThat(nut.getPresentations()).allMatch(p -> "admin".equals(p.getCreatedBy()));
        // Two packages were created with their own price
        verify(priceChangeRepository, times(2)).save(any(ProductPriceChange.class));
    }

    @Test
    @DisplayName("CAT-15 por unidad: Caja ×12 a Bs 44 y Display ×24 que no se vende, con su costo 12 × 2,13 y 24 × 2,13")
    void unitProductPackages() {
        List<String> warnings = service.applyPresentations(bar, List.of(
            request("Display ×24", "24", "90.00", false, null),
            request("Caja ×12", "12", "44.00", true, null)), null, null, "admin");

        assertThat(warnings).isEmpty();
        List<ProductPresentationResponse> responses = responses(bar);
        assertThat(responses).extracting(ProductPresentationResponse::name).containsExactly("Caja ×12", "Display ×24");
        assertThat(responses.get(0).price()).isEqualByComparingTo("44.00");
        assertThat(responses.get(0).cost()).isEqualByComparingTo("25.56");
        assertThat(responses.get(0).sold()).isTrue();
        // Not sold: no price, and the price that came with it is dropped
        assertThat(responses.get(1).sold()).isFalse();
        assertThat(responses.get(1).price()).isNull();
        assertThat(responses.get(1).fixedPrice()).isNull();
        assertThat(responses.get(1).cost()).isEqualByComparingTo("51.12");
    }

    @Test
    @DisplayName("CAT-15: sin precio propio la caja vale equivalencia × precio (12 × 4,00 = 48,00); bajo el costo avisa")
    void unitPackagePriceWithoutOwnPrice() {
        List<String> warnings = service.applyPresentations(bar, List.of(
            request("Caja ×12", "12", null),
            request("Display ×24", "24", "50.00")), null, null, "admin");

        assertThat(responses(bar)).extracting(r -> r.price().toPlainString()).containsExactly("48.00", "50.00");
        assertThat(warnings).containsExactly("El empaque 'Display ×24' se vende a pérdida.");
    }

    @Test
    @DisplayName("CAT-15 por unidad: la equivalencia es un entero desde 2, sin repetirse")
    void unitEquivalenceIsWholeFromTwo() {
        assertThatThrownBy(() -> service.applyPresentations(bar, List.of(request("Caja", "1", null)), null, null, "admin"))
            .hasMessage("La equivalencia de 'Caja' debe ser un número entero mayor a 1.");
        assertThatThrownBy(() -> service.applyPresentations(bar, List.of(request("Caja", "12.5", null)), null, null, "admin"))
            .hasMessage("La equivalencia de 'Caja' debe ser un número entero mayor a 1.");
        assertThatThrownBy(() -> service.applyPresentations(bar, List.of(
            request("Caja ×12", "12", null), request("Docena", "12", null)), null, null, "admin"))
            .hasMessage("Ya existe un empaque de 12 u en este producto.");
        verify(presentationRepository, never()).save(any(ProductPresentation.class));
    }

    @Test
    @DisplayName("Rechaza nombre o peso repetidos, peso con más de 3 decimales, precio propio en 0 y más de 6 activos")
    void rejectsInvalidPresentations() {
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(
            request("1/2 libra", "0.227", null), request("1/2 LIBRA", "0.250", null)), null, null, "admin"))
            .hasMessage("Ya existe el empaque '1/2 LIBRA' en este producto.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(
            request("1/2 libra", "0.227", null), request("Media libra", "0.2270", null)), null, null, "admin"))
            .hasMessage("Ya existe un empaque de " + StoreFormat.number(new BigDecimal("0.227"), 3) + " kg en este producto.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request("x", "0.1005", null)), null, null, "admin"))
            .hasMessage("El peso admite hasta 3 decimales (gramos).");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request("x", "0", null)), null, null, "admin"))
            .hasMessage("El peso debe ser mayor a 0 kg.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request(" ", "0.100", null)), null, null, "admin"))
            .hasMessage("El nombre del empaque es obligatorio.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request("x", "0.100", "0")), null, null, "admin"))
            .hasMessage("El precio propio debe ser mayor a 0.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request("x", "0.100", "1.005")), null, null, "admin"))
            .hasMessage("El precio propio admite hasta 2 decimales.");
        List<ProductPresentationRequest> seven = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            seven.add(request(i + "00 g", "0." + i + "00", null));
        }
        assertThatThrownBy(() -> service.applyPresentations(nut, seven, null, null, "admin"))
            .hasMessage("Un producto admite hasta 6 empaques activos.");
        verify(presentationRepository, never()).save(any(ProductPresentation.class));
    }

    @Test
    @DisplayName("Lo que no viene en la lista se desactiva, no se borra, y libera su código de barras")
    void deactivatesWhatIsLeftOut() {
        ProductPresentation hundred = existing("100 g", "0.100", null);
        ProductPresentation half = existing("1/2 libra", "0.227", null);

        service.applyPresentations(nut, List.of(keep(half, null)), null, null, "operador");

        assertThat(hundred.isActive()).isFalse();
        assertThat(hundred.getUpdatedBy()).isEqualTo("operador");
        assertThat(half.isActive()).isTrue();
        assertThat(half.getUpdatedBy()).isNull();
        assertThat(nut.getPresentations()).hasSize(2);
        verify(barcodeService).applyPresentationBarcode(nut, hundred, "", "operador");
        verify(barcodeService, never()).applyPresentationBarcode(eq(nut), eq(half), any(), any());
    }

    @Test
    @DisplayName("CAT-15: al cambiar entre kilo y unidad se desactivan los empaques activos, aunque el formulario los mande")
    void deactivatesWhenTheSaleUnitChanges() {
        ProductPresentation half = existing("1/2 libra", "0.227", "45.00");
        nut.setSaleUnit(SaleUnit.UNIT);

        List<String> warnings = service.applyPresentations(nut, List.of(keep(half, "45.00"), request("Caja ×12", "12", null)),
            null, SaleUnit.KILOGRAM, "admin");

        assertThat(warnings).isEmpty();
        assertThat(half.isActive()).isFalse();
        assertThat(nut.getPresentations()).filteredOn(ProductPresentation::isActive)
            .extracting(ProductPresentation::getName).containsExactly("Caja ×12");
    }

    @Test
    @DisplayName("CAT-15: el código de cada empaque pasa al servicio de códigos; sin código no se toca")
    void appliesPackageBarcodes() {
        ProductPresentation box = existing(bar, "Caja ×12", "12", "44.00");

        service.applyPresentations(bar, List.of(
            new ProductPresentationRequest(box.getId(), "Caja ×12", new BigDecimal("12"), new BigDecimal("44.00"), true, true, ""),
            request("Display ×24", "24", null, false, "7790000000017"),
            request("Pack ×6", "6", null)), null, null, "admin");

        ProductPresentation display = bar.getPresentations().stream().filter(p -> p.getName().equals("Display ×24")).findFirst().orElseThrow();
        ProductPresentation pack = bar.getPresentations().stream().filter(p -> p.getName().equals("Pack ×6")).findFirst().orElseThrow();
        verify(barcodeService).applyPresentationBarcode(bar, box, "", "admin");
        verify(barcodeService).applyPresentationBarcode(bar, display, "7790000000017", "admin");
        verify(barcodeService, never()).applyPresentationBarcode(eq(bar), eq(pack), any(), any());
    }

    @Test
    @DisplayName("Cambiar el precio propio deja la fila en el historial de precios con el empaque")
    void recordsFixedPriceChange() {
        ProductPresentation half = existing("1/2 libra", "0.227", "45.00");

        service.applyPresentations(nut, List.of(keep(half, "47.00")), null, null, "admin");

        ArgumentCaptor<ProductPriceChange> captor = ArgumentCaptor.forClass(ProductPriceChange.class);
        verify(priceChangeRepository).save(captor.capture());
        ProductPriceChange change = captor.getValue();
        assertThat(change.getPresentationId()).isEqualTo(half.getId());
        assertThat(change.getPreviousFixedPrice()).isEqualByComparingTo("45.00");
        assertThat(change.getNewFixedPrice()).isEqualByComparingTo("47.00");
        assertThat(change.getSource()).isEqualTo(PriceChangeSource.CATALOG);
        assertThat(change.getNewSellPrice()).isEqualByComparingTo(change.getPreviousSellPrice());
        assertThat(half.getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Avisa la venta a pérdida y los empaques de precio propio que no cambian con el precio")
    void warnsLossAndFixedPrices() {
        existing("1/4 libra", "0.113", "23.00");
        existing("1/2 libra", "0.227", "45.00");
        existing("100 g", "0.100", null);
        existing("1 kilo", "1.000", "100.00");

        List<String> warnings = service.applyPresentations(nut, null, new BigDecimal("200.00"), null, "admin");

        assertThat(warnings).containsExactly(
            "El empaque '1 kilo' se vende a pérdida.",
            "Estos empaques tienen precio propio y no cambian: 1/4 libra (" + StoreFormat.money(new BigDecimal("23.00"))
                + "), 1/2 libra (" + StoreFormat.money(new BigDecimal("45.00")) + "), 1 kilo ("
                + StoreFormat.money(new BigDecimal("100.00")) + ").");
    }

    @Test
    @DisplayName("CAT-15: para vender, el empaque tiene que estar activo y venderse; para comprar basta que esté activo")
    void findsSoldOrActivePackages() {
        ProductPresentation box = existing(bar, "Caja ×12", "12", "44.00");
        ProductPresentation display = existing(bar, "Display ×24", "24", null);
        display.setSold(false);
        when(presentationRepository.findById(box.getId())).thenReturn(Optional.of(box));
        when(presentationRepository.findById(display.getId())).thenReturn(Optional.of(display));

        assertThat(service.findSoldPresentation(bar, box.getId())).isSameAs(box);
        assertThatThrownBy(() -> service.findSoldPresentation(bar, display.getId()))
            .hasMessage("El empaque 'Display ×24' de 'Barra Chia' ya no está disponible.");
        assertThat(service.findActivePresentation(bar, display.getId())).isSameAs(display);
        box.setActive(false);
        assertThatThrownBy(() -> service.findActivePresentation(bar, box.getId()))
            .hasMessage("El empaque 'Caja ×12' de 'Barra Chia' ya no está disponible.");
    }
}
