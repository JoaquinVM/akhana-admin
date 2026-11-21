package com.akhana.akhana_admin.contract;

import com.akhana.akhana_admin.dto.CashSessionSummaryResponse;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.dto.SaleItemResponse;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.model.CashSessionStatus;
import com.akhana.akhana_admin.model.FinancialCategoryType;
import com.akhana.akhana_admin.model.FinancialTransactionStatus;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.model.ProductSpecialType;
import com.akhana.akhana_admin.model.SaleItemAssignmentStatus;
import com.akhana.akhana_admin.model.SaleStatus;
import com.akhana.akhana_admin.model.SaleType;
import com.akhana.akhana_admin.model.SaleUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract between the Java responses and the TypeScript models: each response, with fixed data and serialized
 * as the API does, must equal its file in contracts/ (repo root). The frontend specs answer with those same files
 * and check their fields against the models, so a field added, renamed or removed on one side fails the other.
 * A change made on purpose rewrites the contracts with CONTRACTS_WRITE=true; then update the TypeScript models.
 */
@JsonTest
class ResponseContractTest {

    private static final Path CONTRACTS = Path.of("..", "contracts");
    private static final boolean WRITE = "true".equals(System.getenv("CONTRACTS_WRITE"));

    private static final UUID SALE = UUID.fromString("5a1e0000-0000-0000-0000-000000000001");
    private static final UUID SESSION = UUID.fromString("ca5e0000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("9d0d0000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER = UUID.fromString("c0570000-0000-0000-0000-000000000001");
    private static final UUID CREDITOR = UUID.fromString("c3ed0000-0000-0000-0000-000000000001");
    private static final UUID CASH = UUID.fromString("acc00000-0000-0000-0000-000000000001");
    private static final UUID BANK = UUID.fromString("acc00000-0000-0000-0000-000000000002");
    // 30/11/2026 at 19:30 in La Paz
    private static final Instant RECORDED = Instant.parse("2026-11-30T23:30:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 30);

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("SaleResponse con sus líneas se serializa como contracts/sale-response.json")
    void saleResponse() throws IOException {
        SaleItemResponse item = new SaleItemResponse(UUID.fromString("5a1e0000-0000-0000-0000-0000000000a1"), PRODUCT,
            "Nueces", "NUE-1", new BigDecimal("90.00"), new BigDecimal("5.00"), new BigDecimal("85.00"),
            new BigDecimal("0.250"), new BigDecimal("21.25"), SaleUnit.KILOGRAM, null, null, null,
            new BigDecimal("0.250"), ProductSpecialType.THIRD_PARTY, "Nueces de Ana", SaleItemAssignmentStatus.ASIGNADO,
            "admin", RECORDED, CREDITOR, "Ana");
        SaleResponse sale = new SaleResponse(SALE, "VTA-000042", SESSION, 7L, SaleStatus.COMPLETADA,
            new BigDecimal("22.50"), new BigDecimal("1.25"), new BigDecimal("1.25"), new BigDecimal("2.50"),
            new BigDecimal("20.00"), PaymentMethod.MIXTO, new BigDecimal("10.00"), new BigDecimal("5.00"),
            new BigDecimal("20.00"), new BigDecimal("10.00"), "Venta de prueba", "cajero", RECORDED, null, null, null,
            List.of(item), SaleType.NORMAL, CUSTOMER, "Luis", new BigDecimal("5.00"), false, DAY,
            BigDecimal.ZERO);

        assertMatchesContract("sale-response.json", sale);
    }

    @Test
    @DisplayName("CashSessionSummaryResponse se serializa como contracts/cash-session-response.json")
    void cashSessionResponse() throws IOException {
        CashSessionSummaryResponse session = new CashSessionSummaryResponse(SESSION, 7L, CashSessionStatus.CERRADA,
            new BigDecimal("100.00"), "Apertura", "cajero", Instant.parse("2026-11-30T12:00:00Z"),
            new BigDecimal("245.50"), "Cierre", "cajero", RECORDED, new BigDecimal("150.00"), new BigDecimal("80.00"),
            new BigDecimal("230.00"), new BigDecimal("245.00"), new BigDecimal("0.50"), 12L, false, DAY, true);

        assertMatchesContract("cash-session-response.json", session);
    }

    @Test
    @DisplayName("FinancialTransactionResponse con cuentas y categoría se serializa como contracts/financial-transaction-response.json")
    void financialTransactionResponse() throws IOException {
        FinancialTransactionResponse tx = FinancialTransactionResponse.builder()
            .id(UUID.fromString("f1000000-0000-0000-0000-000000000001"))
            .type(FinancialTransactionType.TRANSFERENCIA)
            .amount(new BigDecimal("150.00"))
            .transactionDate(RECORDED)
            .operationDate(DAY)
            .description("Retiro de caja a Cuenta bancaria BISA")
            .notes("Depósito")
            .status(FinancialTransactionStatus.ACTIVO)
            .cashSessionId(SESSION)
            .historical(false)
            .sourceAccount(new FinancialTransactionResponse.AccountSummary(CASH, "Caja", "#2E5B27", true))
            .destinationAccount(new FinancialTransactionResponse.AccountSummary(BANK, "Cuenta bancaria BISA", "#1B3B18", false))
            .category(new FinancialTransactionResponse.CategorySummary(
                UUID.fromString("ca700000-0000-0000-0000-000000000001"), "Servicios", FinancialCategoryType.GASTO, "#F5B800"))
            .createdBy("cajero")
            .createdAt(RECORDED)
            .build();

        assertMatchesContract("financial-transaction-response.json", tx);
    }

    private void assertMatchesContract(String file, Object response) throws IOException {
        String actual = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        if (WRITE) {
            Files.writeString(CONTRACTS.resolve(file), actual + "\n");
        }
        JsonNode expected = jsonMapper.readTree(Files.readString(CONTRACTS.resolve(file)));
        assertThat(jsonMapper.readTree(actual))
            .as("%s changed: if on purpose, rerun with CONTRACTS_WRITE=true and update the TypeScript model", file)
            .isEqualTo(expected);
    }
}
