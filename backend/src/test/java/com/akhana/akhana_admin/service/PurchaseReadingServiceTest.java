package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.PurchaseDraftResponse;
import com.akhana.akhana_admin.dto.PurchaseReadingStatusResponse;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.PurchaseReadingRepository;
import com.akhana.akhana_admin.repository.PurchaseRepository;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.service.PurchaseReader.ReadLine;
import com.akhana.akhana_admin.service.PurchaseReader.ReadOutcome;
import com.akhana.akhana_admin.service.PurchaseReader.ReadPurchase;
import com.akhana.akhana_admin.service.impl.ProductPhotoProcessor;
import com.akhana.akhana_admin.service.impl.PurchaseReadingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// COM-20 to COM-22, COM-25: what a reading proposes, with the AI simulated. No test here reaches OpenAI (CA-29)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseReadingServiceTest {

    @Mock private PurchaseReader reader;
    @Mock private PurchaseReadingRepository readingRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ProductPhotoProcessor photoProcessor;

    private PurchaseReadingServiceImpl service;
    private Supplier flora;
    private Supplier other;
    private Product jamaica;
    private Product cepilloAzul;
    private Product cepilloRojo;

    @BeforeEach
    void setUp() {
        service = new PurchaseReadingServiceImpl(reader, readingRepository, purchaseRepository, productRepository,
            supplierRepository, photoProcessor, new BigDecimal("10.00"), new BigDecimal("1.00"), new BigDecimal("5.00"));
        flora = Supplier.builder().id(UUID.randomUUID()).name("Flora").code("FL").status(SupplierStatus.ACTIVO).build();
        other = Supplier.builder().id(UUID.randomUUID()).name("Granel").code("GR").status(SupplierStatus.ACTIVO).build();
        jamaica = product("Jamaica 130ml", "JAM", "12.50", flora);
        cepilloAzul = product("Cepillo Azul", "CEA", "4.00", flora);
        cepilloRojo = product("Cepillo Rojo", "CER", "4.00", other);

        when(reader.isConfigured()).thenReturn(true);
        when(reader.model()).thenReturn("gpt-6.1-terra");
        when(readingRepository.sumCostBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(readingRepository.save(any())).thenAnswer(invocation -> {
            PurchaseReading reading = invocation.getArgument(0);
            reading.setId(UUID.randomUUID());
            return reading;
        });
        when(productRepository.findByStatusNotOrderByNameAsc(ProductStatus.ELIMINADO))
            .thenReturn(List.of(cepilloAzul, cepilloRojo, jamaica));
        when(supplierRepository.findByStatusOrderByNameAsc(SupplierStatus.ACTIVO)).thenReturn(List.of(flora, other));
        when(purchaseRepository.findPendingDelivery(any())).thenReturn(List.of());
        when(photoProcessor.process(any())).thenReturn(new ProductPhotoProcessor.ProcessedPhoto(new byte[] {1}, new byte[] {2}));
    }

    private static Product product(String name, String code, String cost, Supplier supplier) {
        return Product.builder().id(UUID.randomUUID()).name(name).code(code).buyPrice(new BigDecimal(cost))
            .sellPrice(new BigDecimal("20.00")).status(ProductStatus.ACTIVO).saleUnit(SaleUnit.UNIT).supplier(supplier).build();
    }

    private static ReadLine textLine(String product, String quantity, String written, boolean doubtful) {
        return new ReadLine(product, quantity != null ? new BigDecimal(quantity) : null, written, doubtful, null, null, null);
    }

    private static ReadPurchase purchase(String supplier, ReadLine... lines) {
        return new ReadPurchase(supplier, false, false, false, List.of(lines), null, null);
    }

    private void textAnswers(ReadPurchase purchase) {
        when(reader.readText(any())).thenReturn(new ReadOutcome(purchase, "{\"raw\":true}", 1000L, 100L));
    }

    private void photoAnswers(ReadPurchase purchase) {
        when(reader.readPhoto(any())).thenReturn(new ReadOutcome(purchase, "{\"raw\":true}", 1000L, 100L));
    }

    @Test
    void textFillsTheLineWithTheSystemCostAndCreatesNoPurchase() {
        textAnswers(purchase(null, textLine("jamaica 130ml", "2", "2", false)));

        PurchaseDraftResponse draft = service.readText("2 jamaica 130ml", "ana");

        PurchaseDraftResponse.Line line = draft.lines().get(0);
        assertThat(line.quantity()).isEqualByComparingTo("2");
        assertThat(line.productId()).isEqualTo(jamaica.getId());
        assertThat(line.unitCost()).isEqualByComparingTo("12.50");
        assertThat(draft.source()).isEqualTo(PurchaseReadingSource.TEXT);
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void severalMatchesAreSuggestedAndLeftWithoutProductOrCost() {
        textAnswers(purchase(null, textLine("cepillo", "1", "1", false)));

        PurchaseDraftResponse.Line line = service.readText("1 cepillo", "ana").lines().get(0);

        assertThat(line.productId()).isNull();
        assertThat(line.unitCost()).isNull();
        assertThat(line.suggestions()).extracting(PurchaseDraftResponse.ProductSuggestion::name)
            .containsExactlyInAnyOrder("Cepillo Azul", "Cepillo Rojo");
    }

    @Test
    void noMatchLeavesNoSuggestionForTheSearchBox() {
        textAnswers(purchase(null, textLine("xyzzy", "1", "1", false)));

        PurchaseDraftResponse.Line line = service.readText("1 xyzzy", "ana").lines().get(0);

        assertThat(line.productId()).isNull();
        assertThat(line.suggestions()).isEmpty();
    }

    @Test
    void theOnlyMatchOfTheSupplierIsChosen() {
        textAnswers(purchase("Flora", textLine("cepillo", "1", "1", false)));

        PurchaseDraftResponse draft = service.readText("Flora: 1 cepillo", "ana");

        assertThat(draft.supplierId()).isEqualTo(flora.getId());
        assertThat(draft.lines().get(0).productId()).isEqualTo(cepilloAzul.getId());
    }

    @Test
    void aMissingQuantityProposesOneAsDoubtful() {
        textAnswers(purchase(null, textLine("jamaica", null, null, false)));

        PurchaseDraftResponse.Line line = service.readText("jamaica", "ana").lines().get(0);

        assertThat(line.quantity()).isEqualByComparingTo("1");
        assertThat(line.quantityDoubtful()).isTrue();
    }

    @Test
    void aRangeKeepsTheWrittenTextToHighlightIt() {
        textAnswers(purchase(null, textLine("jamaica", "4", "4 a 5", true)));

        PurchaseDraftResponse.Line line = service.readText("4 a 5 jamaica", "ana").lines().get(0);

        assertThat(line.quantity()).isEqualByComparingTo("4");
        assertThat(line.quantityDoubtful()).isTrue();
        assertThat(line.quantityText()).isEqualTo("4 a 5");
    }

    @Test
    void severalSuppliersFillNothingButTheCallCounts() {
        textAnswers(new ReadPurchase(null, true, false, false, List.of(textLine("jamaica", "1", "1", false)), null, null));

        assertThatThrownBy(() -> service.readText("Flora: 1. Granel: 2", "ana"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(PurchaseReadingService.ONE_SUPPLIER);
        verify(readingRepository).save(any());
    }

    @Test
    void anUnnamedOrUnknownSupplierStaysEmpty() {
        textAnswers(purchase(null, textLine("jamaica", "1", "1", false)));
        assertThat(service.readText("1 jamaica", "ana").supplierId()).isNull();

        textAnswers(purchase("Nadie Conocido", textLine("jamaica", "1", "1", false)));
        assertThat(service.readText("1 jamaica", "ana").supplierId()).isNull();
    }

    @Test
    void aCostInATextIsIgnored() {
        textAnswers(purchase(null, new ReadLine("jamaica 130ml", new BigDecimal("2"), "2", false,
            new BigDecimal("99.00"), new BigDecimal("198.00"), null)));

        assertThat(service.readText("2 jamaica a 99", "ana").lines().get(0).unitCost()).isEqualByComparingTo("12.50");
    }

    @Test
    void thePhotoKeepsTheInvoiceCostShippingTotalAndPaidNote() {
        photoAnswers(new ReadPurchase("Flora", false, false, true,
            List.of(new ReadLine("jamaica 130ml", new BigDecimal("2"), "2", false, new BigDecimal("13.25"), null, null)),
            new BigDecimal("15"), new BigDecimal("41.50")));

        PurchaseDraftResponse draft = service.readPhoto(new byte[] {9}, "ana");

        assertThat(draft.lines().get(0).unitCost()).isEqualByComparingTo("13.25");
        assertThat(draft.shippingCost()).isEqualByComparingTo("15.00");
        assertThat(draft.readTotal()).isEqualByComparingTo("41.50");
        assertThat(draft.paidNote()).isTrue();
    }

    @Test
    void anAmountWithoutUnitCostIsDividedWithHalfUp() {
        photoAnswers(purchase("Flora", new ReadLine("jamaica 130ml", new BigDecimal("3"), "3", false, null, new BigDecimal("100.00"), null)));

        PurchaseDraftResponse.Line line = service.readPhoto(new byte[] {9}, "ana").lines().get(0);

        assertThat(line.unitCost()).isEqualByComparingTo("33.33");
        assertThat(line.costNote()).contains("100").contains("3");
    }

    @Test
    void anUnknownCostStaysBlankWithTheCalculationMessage() {
        photoAnswers(purchase("Flora", new ReadLine("jamaica 130ml", new BigDecimal("3"), "3", false, null, null, "200 ÷ 25 = 8, si todos cuestan igual")));

        PurchaseDraftResponse.Line line = service.readPhoto(new byte[] {9}, "ana").lines().get(0);

        assertThat(line.unitCost()).isNull();
        assertThat(line.costNote()).isEqualTo("200 ÷ 25 = 8, si todos cuestan igual");
    }

    @Test
    void thePhotoOffersThePendingPurchasesOfItsSupplier() {
        Purchase pending = Purchase.builder().id(UUID.randomUUID()).purchaseNumber("PED-000007")
            .purchaseDate(LocalDate.of(2026, 11, 1)).itemsTotal(new BigDecimal("80.00")).costToConfirm(true).build();
        when(purchaseRepository.findPendingDelivery(flora.getId())).thenReturn(List.of(pending));
        photoAnswers(purchase("Flora", new ReadLine("jamaica 130ml", new BigDecimal("1"), "1", false, new BigDecimal("13.00"), null, null)));

        PurchaseDraftResponse draft = service.readPhoto(new byte[] {9}, "ana");

        assertThat(draft.pendingPurchases()).singleElement().satisfies(p -> {
            assertThat(p.purchaseNumber()).isEqualTo("PED-000007");
            assertThat(p.costToConfirm()).isTrue();
        });
    }

    @Test
    void anInvalidPhotoNeverReachesTheAi() {
        when(photoProcessor.process(any())).thenThrow(new IllegalArgumentException(ProductPhotoService.INVALID_FILE));

        assertThatThrownBy(() -> service.readPhoto(new byte[] {9}, "ana")).hasMessage(ProductPhotoService.INVALID_FILE);
        verify(reader, never()).readPhoto(any());
        verify(readingRepository, never()).save(any());
    }

    @Test
    void theAiReceivesTheReducedPhotoNotTheOriginal() {
        photoAnswers(purchase("Flora", new ReadLine("jamaica 130ml", new BigDecimal("1"), "1", false, new BigDecimal("13.00"), null, null)));

        service.readPhoto(new byte[] {9, 9, 9}, "ana");

        verify(reader).readPhoto(new byte[] {1});
    }

    @Test
    void aFailureSaysSoCreatesNothingAndStillCountsTheCall() {
        when(reader.readText(any())).thenReturn(ReadOutcome.failed(null, null));

        assertThatThrownBy(() -> service.readText("2 jamaica", "ana"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(PurchaseReadingService.READ_FAILED);

        ArgumentCaptor<PurchaseReading> saved = ArgumentCaptor.forClass(PurchaseReading.class);
        verify(readingRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PurchaseReadingStatus.FAILED);
        assertThat(saved.getValue().getCostUsd()).isPositive();
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void anAnswerWithoutLinesIsAFailure() {
        textAnswers(purchase(null));

        assertThatThrownBy(() -> service.readText("hola", "ana")).hasMessage(PurchaseReadingService.READ_FAILED);
    }

    @Test
    void theCostComesFromTheUsageAndIsRoundedUp() {
        when(reader.readText(any())).thenReturn(new ReadOutcome(
            purchase(null, textLine("jamaica", "1", "1", false)), "{}", 1001L, 333L));

        service.readText("1 jamaica", "ana");

        ArgumentCaptor<PurchaseReading> saved = ArgumentCaptor.forClass(PurchaseReading.class);
        verify(readingRepository).save(saved.capture());
        // 1001 × 1.00 + 333 × 5.00 = 2666 per million
        assertThat(saved.getValue().getCostUsd()).isEqualByComparingTo("0.002666");
        assertThat(saved.getValue().getResult()).isEqualTo("{}");
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("ana");
    }

    @Test
    void atTheCapNothingIsCalled() {
        when(readingRepository.sumCostBetween(any(), any())).thenReturn(new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.readText("2 jamaica", "ana")).hasMessage(PurchaseReadingService.CAP_REACHED);
        assertThatThrownBy(() -> service.readPhoto(new byte[] {9}, "ana")).hasMessage(PurchaseReadingService.CAP_REACHED);
        verifyNoInteractions(photoProcessor);
        verify(reader, never()).readText(any());
    }

    @Test
    void withoutTheKeyNothingIsCalled() {
        when(reader.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.readText("2 jamaica", "ana")).hasMessage(PurchaseReadingService.NOT_CONFIGURED);
        verify(reader, never()).readText(any());
    }

    @Test
    void onlyTheAdministratorGetsTheSpend() {
        when(readingRepository.sumCostBetween(any(), any())).thenReturn(new BigDecimal("3.456"));

        PurchaseReadingStatusResponse admin = service.getStatus(true);
        PurchaseReadingStatusResponse operator = service.getStatus(false);

        assertThat(admin.spentUsd()).isEqualByComparingTo("3.46");
        assertThat(admin.capUsd()).isEqualByComparingTo("10.00");
        assertThat(admin.capReached()).isFalse();
        assertThat(operator.spentUsd()).isNull();
        assertThat(operator.capUsd()).isNull();
    }
}
