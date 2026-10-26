package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ConsumptionItemRequest;
import com.akhana.akhana_admin.dto.ConsumptionRequest;
import com.akhana.akhana_admin.dto.ConsumptionResponse;
import com.akhana.akhana_admin.dto.VoidConsumptionRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ConsumptionRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.impl.ConsumptionServiceImpl;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsumptionServiceTest {

    @Mock private ConsumptionRepository consumptionRepository;
    @Mock private ProductRepository productRepository;

    private ConsumptionServiceImpl consumptionService;

    private Product roquin;
    private Product bombitas;
    private Product nuts;

    @BeforeEach
    void setUp() {
        // Only these two repositories: a consumption cannot reach a cash session, an account or a movement
        consumptionService = new ConsumptionServiceImpl(consumptionRepository, productRepository);

        roquin = product("Roquin", "ROQ-1", SaleUnit.UNIT, "2.73", "4.00");
        bombitas = product("Bombitas Proteína", "BOM-1", SaleUnit.UNIT, "10.00", "15.00");
        nuts = product("Nueces", "NUE-1", SaleUnit.KILOGRAM, "60.00", "90.00");

        when(consumptionRepository.nextConsumptionNumber()).thenReturn(1L);
        when(consumptionRepository.save(any(Consumption.class))).thenAnswer(inv -> {
            Consumption c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
    }

    private Product product(String name, String code, SaleUnit unit, String cost, String price) {
        Product product = Product.builder().id(UUID.randomUUID()).code(code).name(name).saleUnit(unit)
            .buyPrice(new BigDecimal(cost)).sellPrice(new BigDecimal(price)).status(ProductStatus.ACTIVO).build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        return product;
    }

    private ConsumptionItemRequest line(Product product, String quantity) {
        return new ConsumptionItemRequest(product.getId(), new BigDecimal(quantity));
    }

    private ConsumptionRequest request(LocalDate date, ConsumptionItemRequest... items) {
        return new ConsumptionRequest(date, "  Ali  ", "  ", List.of(items));
    }

    private Consumption existing(ConsumptionStatus status) {
        Consumption consumption = Consumption.builder().id(UUID.randomUUID()).consumptionNumber("CON-000001")
            .consumptionDate(LocalDate.now()).totalAmount(new BigDecimal("5.46")).status(status)
            .createdBy("operator").build();
        when(consumptionRepository.findByIdForUpdate(consumption.getId())).thenReturn(Optional.of(consumption));
        return consumption;
    }

    @Test
    @DisplayName("Copia el costo del producto, calcula líneas y total, y no mueve dinero")
    void register_copiesCostAndComputesTotal() {
        ConsumptionResponse response = consumptionService.registerConsumption(
            request(null, line(roquin, "2"), line(bombitas, "1")), "operator");

        assertThat(response.consumptionNumber()).isEqualTo("CON-000001");
        assertThat(response.consumptionDate()).isEqualTo(LocalDate.now(ZoneId.of("America/La_Paz")));
        assertThat(response.consumedFor()).isEqualTo("Ali");
        assertThat(response.comment()).isNull();
        assertThat(response.status()).isEqualTo(ConsumptionStatus.REGISTRADO);
        assertThat(response.createdBy()).isEqualTo("operator");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).unitCost()).isEqualByComparingTo("2.73");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("5.46");
        assertThat(response.items().get(1).subtotal()).isEqualByComparingTo("10.00");
        assertThat(response.totalAmount()).isEqualTo(new BigDecimal("15.46"));
    }

    @Test
    @DisplayName("0,100 kg de un producto a Bs 60,00 el kilo vale Bs 6,00")
    void register_weightQuantity() {
        ConsumptionResponse response = consumptionService.registerConsumption(request(null, line(nuts, "0.100")), "operator");

        assertThat(response.items().get(0).quantity()).isEqualTo(new BigDecimal("0.100"));
        assertThat(response.items().get(0).subtotal()).isEqualTo(new BigDecimal("6.00"));
    }

    @Test
    @DisplayName("Un cambio posterior del costo no altera el consumo registrado")
    void register_costSnapshotSurvivesLaterChanges() {
        ArgumentCaptor<Consumption> captor = ArgumentCaptor.forClass(Consumption.class);
        consumptionService.registerConsumption(request(null, line(roquin, "1")), "operator");
        verify(consumptionRepository).save(captor.capture());

        roquin.setBuyPrice(new BigDecimal("3.10"));

        ConsumptionItem item = captor.getValue().getItems().get(0);
        assertThat(item.getUnitCost()).isEqualByComparingTo("2.73");
        assertThat(item.getProductName()).isEqualTo("Roquin");
        assertThat(item.getProductCode()).isEqualTo("ROQ-1");
    }

    @Test
    @DisplayName("El mismo producto dos veces suma la cantidad en una línea")
    void register_mergesRepeatedProduct() {
        ConsumptionResponse response = consumptionService.registerConsumption(
            request(null, line(roquin, "1"), line(roquin, "2")), "operator");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualByComparingTo("3");
        assertThat(response.totalAmount()).isEqualByComparingTo("8.19");
    }

    @Test
    @DisplayName("Acepta una fecha anterior y rechaza una futura")
    void register_dateRules() {
        LocalDate today = LocalDate.now(ZoneId.of("America/La_Paz"));
        ConsumptionResponse past = consumptionService.registerConsumption(
            request(today.minusDays(3), line(roquin, "1")), "operator");
        assertThat(past.consumptionDate()).isEqualTo(today.minusDays(3));

        assertThatThrownBy(() -> consumptionService.registerConsumption(
            request(today.plusDays(1), line(roquin, "1")), "operator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La fecha del consumo no puede ser futura.");
    }

    @Test
    @DisplayName("Sin líneas se rechaza")
    void register_requiresItems() {
        assertThatThrownBy(() -> consumptionService.registerConsumption(
            new ConsumptionRequest(null, null, null, List.of()), "operator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El consumo debe tener al menos una línea.");
        verify(consumptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un producto por unidad solo acepta cantidades enteras (CAT-08)")
    void register_unitProductRequiresWholeQuantity() {
        assertThatThrownBy(() -> consumptionService.registerConsumption(request(null, line(roquin, "1.5")), "operator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La cantidad de 'Roquin' debe ser entera: se vende por unidad.");
        assertThatThrownBy(() -> consumptionService.registerConsumption(request(null, line(nuts, "0")), "operator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La cantidad de 'Nueces' debe ser mayor a 0.");
    }

    @Test
    @DisplayName("Rechaza un producto eliminado y acepta uno inactivo")
    void register_productStatus() {
        roquin.setStatus(ProductStatus.ELIMINADO);
        assertThatThrownBy(() -> consumptionService.registerConsumption(request(null, line(roquin, "1")), "operator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El producto 'Roquin' no existe o fue eliminado.");

        bombitas.setStatus(ProductStatus.INACTIVO);
        ConsumptionResponse response = consumptionService.registerConsumption(request(null, line(bombitas, "1")), "operator");
        assertThat(response.totalAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Un producto con costo 0 se acepta y vale Bs 0,00")
    void register_zeroCostProduct() {
        roquin.setBuyPrice(BigDecimal.ZERO);
        ConsumptionResponse response = consumptionService.registerConsumption(request(null, line(roquin, "2")), "operator");
        assertThat(response.totalAmount()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Anular con motivo deja el consumo ANULADO con quién y cuándo")
    void void_marksVoided() {
        Consumption consumption = existing(ConsumptionStatus.REGISTRADO);

        ConsumptionResponse response = consumptionService.voidConsumption(consumption.getId(),
            new VoidConsumptionRequest("  Registrado dos veces  "), "admin");

        assertThat(response.status()).isEqualTo(ConsumptionStatus.ANULADO);
        assertThat(response.voidedBy()).isEqualTo("admin");
        assertThat(response.voidedAt()).isNotNull();
        assertThat(response.voidReason()).isEqualTo("Registrado dos veces");
    }

    @Test
    @DisplayName("Anular exige motivo y no repite una anulación")
    void void_rules() {
        Consumption voided = existing(ConsumptionStatus.ANULADO);
        assertThatThrownBy(() -> consumptionService.voidConsumption(voided.getId(), new VoidConsumptionRequest(" "), "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El motivo de anulación es obligatorio.");
        assertThatThrownBy(() -> consumptionService.voidConsumption(voided.getId(), new VoidConsumptionRequest("Otra vez"), "admin"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("El consumo ya está anulado.");
    }

    @Test
    @DisplayName("El listado pasa los filtros y recorta el texto")
    void list_passesFilters() {
        UUID productId = roquin.getId();
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 25);
        Consumption consumption = existing(ConsumptionStatus.REGISTRADO);
        when(consumptionRepository.search(from, to, ConsumptionStatus.REGISTRADO, productId, "ali"))
            .thenReturn(List.of(consumption));

        List<ConsumptionResponse> list = consumptionService.getConsumptions(from, to, productId,
            ConsumptionStatus.REGISTRADO, "  ali ");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).items()).isEmpty();
        verify(consumptionRepository).search(from, to, ConsumptionStatus.REGISTRADO, productId, "ali");
    }
}
