package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.dto.ProductPresentationResponse;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ProductPresentationRepository;
import com.akhana.akhana_admin.repository.ProductPriceChangeRepository;
import com.akhana.akhana_admin.service.impl.ProductPresentationServiceImpl;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CAT-11 with the Nuez pelada of the spec: cost 130,00 and price 210,00 per kilo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductPresentationServiceTest {

    @Mock private ProductPresentationRepository presentationRepository;
    @Mock private ProductPriceChangeRepository priceChangeRepository;

    private ProductPresentationServiceImpl service;
    private Product nut;

    @BeforeEach
    void setUp() {
        service = new ProductPresentationServiceImpl(presentationRepository, priceChangeRepository);
        nut = Product.builder().id(UUID.randomUUID()).code("FSE-NUE").name("Nuez pelada").saleUnit(SaleUnit.KILOGRAM)
            .buyPrice(new BigDecimal("130.00")).sellPrice(new BigDecimal("210.00")).status(ProductStatus.ACTIVO)
            .presentations(new ArrayList<>()).build();
        when(presentationRepository.save(any(ProductPresentation.class))).thenAnswer(inv -> {
            ProductPresentation p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    private static ProductPresentationRequest request(String name, String weight, String fixedPrice) {
        return new ProductPresentationRequest(null, name, weight != null ? new BigDecimal(weight) : null,
            fixedPrice != null ? new BigDecimal(fixedPrice) : null, true);
    }

    private ProductPresentation existing(String name, String weight, String fixedPrice) {
        ProductPresentation presentation = ProductPresentation.builder().id(UUID.randomUUID()).product(nut).name(name)
            .weightKg(new BigDecimal(weight)).fixedPrice(fixedPrice != null ? new BigDecimal(fixedPrice) : null)
            .active(true).createdBy("admin").build();
        nut.getPresentations().add(presentation);
        return presentation;
    }

    private static ProductPresentationRequest keep(ProductPresentation p, String fixedPrice) {
        return new ProductPresentationRequest(p.getId(), p.getName(), p.getWeightKg(),
            fixedPrice != null ? new BigDecimal(fixedPrice) : null, true);
    }

    @Test
    @DisplayName("Crea las presentaciones con precio fijo o calculado y costo siempre calculado, de menor a mayor peso")
    void createsPresentationsWithPriceAndCost() {
        List<String> warnings = service.applyPresentations(nut, List.of(
            request("1 libra", "0.454", "90.00"),
            request("100 g", "0.100", null),
            request(" 1/2 libra ", "0.227", "45")), null, "admin");

        assertThat(warnings).isEmpty();
        List<ProductPresentationResponse> responses = nut.getPresentations().stream()
            .map(p -> ProductPresentationResponse.fromEntity(p, nut)).toList();
        assertThat(responses).extracting(ProductPresentationResponse::name).containsExactly("100 g", "1/2 libra", "1 libra");
        assertThat(responses).extracting(r -> r.price().toPlainString()).containsExactly("21.00", "45.00", "90.00");
        assertThat(responses).extracting(r -> r.cost().toPlainString()).containsExactly("13.00", "29.51", "59.02");
        assertThat(nut.getPresentations()).allMatch(p -> "admin".equals(p.getCreatedBy()));
        // Two presentations were created with a fixed price
        verify(priceChangeRepository, times(2)).save(any(ProductPriceChange.class));
    }

    @Test
    @DisplayName("Rechaza nombre o peso repetidos, peso con más de 3 decimales, precio fijo en 0 y más de 6 activas")
    void rejectsInvalidPresentations() {
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(
            request("1/2 libra", "0.227", null), request("1/2 LIBRA", "0.250", null)), null, "admin"))
            .hasMessage("Ya existe la presentación '1/2 LIBRA' en este producto.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(
            request("1/2 libra", "0.227", null), request("Media libra", "0.2270", null)), null, "admin"))
            .hasMessage("Ya existe una presentación de 0,227 kg en este producto.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request("x", "0.1005", null)), null, "admin"))
            .hasMessage("El peso admite hasta 3 decimales (gramos).");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request("x", "0", null)), null, "admin"))
            .hasMessage("El peso debe ser mayor a 0 kg.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request(" ", "0.100", null)), null, "admin"))
            .hasMessage("El nombre de la presentación es obligatorio.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request("x", "0.100", "0")), null, "admin"))
            .hasMessage("El precio fijo debe ser mayor a 0.");
        assertThatThrownBy(() -> service.applyPresentations(nut, List.of(request("x", "0.100", "1.005")), null, "admin"))
            .hasMessage("El precio fijo admite hasta 2 decimales.");
        List<ProductPresentationRequest> seven = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            seven.add(request(i + "00 g", "0." + i + "00", null));
        }
        assertThatThrownBy(() -> service.applyPresentations(nut, seven, null, "admin"))
            .hasMessage("Un producto admite hasta 6 presentaciones activas.");
        verify(presentationRepository, never()).save(any(ProductPresentation.class));
    }

    @Test
    @DisplayName("Lo que no viene en la lista se desactiva, no se borra; se puede volver a crear el mismo nombre")
    void deactivatesWhatIsLeftOut() {
        ProductPresentation hundred = existing("100 g", "0.100", null);
        ProductPresentation half = existing("1/2 libra", "0.227", null);

        service.applyPresentations(nut, List.of(keep(half, null)), null, "operador");

        assertThat(hundred.isActive()).isFalse();
        assertThat(hundred.getUpdatedBy()).isEqualTo("operador");
        assertThat(half.isActive()).isTrue();
        assertThat(half.getUpdatedBy()).isNull();
        assertThat(nut.getPresentations()).hasSize(2);
    }

    @Test
    @DisplayName("Al pasar a Unidad se desactivan todas las presentaciones")
    void deactivatesWhenSoldByUnit() {
        ProductPresentation half = existing("1/2 libra", "0.227", "45.00");
        nut.setSaleUnit(SaleUnit.UNIT);

        List<String> warnings = service.applyPresentations(nut, List.of(keep(half, "45.00")), null, "admin");

        assertThat(warnings).isEmpty();
        assertThat(half.isActive()).isFalse();
    }

    @Test
    @DisplayName("Cambiar el precio fijo deja la fila en el historial de precios con la presentación")
    void recordsFixedPriceChange() {
        ProductPresentation half = existing("1/2 libra", "0.227", "45.00");

        service.applyPresentations(nut, List.of(keep(half, "47.00")), null, "admin");

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
    @DisplayName("Avisa la venta a pérdida y las presentaciones de precio fijo que no cambian con el precio por kilo")
    void warnsLossAndFixedPrices() {
        existing("1/4 libra", "0.113", "23.00");
        existing("1/2 libra", "0.227", "45.00");
        existing("100 g", "0.100", null);
        existing("1 kilo", "1.000", "100.00");

        List<String> warnings = service.applyPresentations(nut, null, new BigDecimal("200.00"), "admin");

        assertThat(warnings).containsExactly(
            "La presentación '1 kilo' se vende a pérdida.",
            "Estas presentaciones tienen precio fijo y no cambian: 1/4 libra (Bs 23,00), 1/2 libra (Bs 45,00), 1 kilo (Bs 100,00).");
    }
}
