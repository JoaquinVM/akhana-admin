package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.SaleService;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 097 (POS-04) against PostgreSQL: cash is charged rounded to Bs 0.10, to the nearest, and the rounding stays in the
 * sale, the session and the Caja account. Everything runs in the transaction of the test, rolled back at the end.
 */
@SpringBootTest
@Transactional
class SaleCashRoundingIntegrationTest {

    private static final String USER = "it-rounding";

    @Autowired private CashSessionService cashSessionService;
    @Autowired private SaleService saleService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private LocalDate day;

    @BeforeEach
    void setUp() {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
        assertThat(open).as("La base ya tiene una caja abierta").isZero();
        CashTestDays.movePastSessionsAway(jdbc);
        day = StoreFormat.today(Clock.systemUTC()).minusDays(2);
        CashTestDays.seedClosedSession(jdbc, day.minusDays(1), "100.00", USER);
        cashSessionService.openSession(new OpenCashRequest(new BigDecimal("100.00"), "Carga", day, null, null), USER);
        next();
    }

    private void next() {
        entityManager.flush();
        entityManager.clear();
    }

    private UUID product(String price) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT categoria " + suffix, USER);
        UUID supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT proveedor " + suffix, "ITP-" + suffix, USER);
        return jdbc.queryForObject("INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, "
            + "percentage_profit, created_by) VALUES (?, ?, ?, ?, 0.00, ?, ?, 0.00, ?) RETURNING id",
            UUID.class, "IT-" + suffix, "IT producto " + suffix, category, supplier, new BigDecimal(price), new BigDecimal(price), USER);
    }

    private SaleResponse sell(String price, PaymentMethod method, String cash, String received, UUID requestId) {
        SaleResponse sale = saleService.registerSale(new SaleDetailRequest(
            List.of(new SaleItemRequest(product(price), BigDecimal.ONE, null)), null, method,
            cash == null ? null : new BigDecimal(cash), null, received == null ? null : new BigDecimal(received),
            null, null, null, null, requestId), USER);
        next();
        return sale;
    }

    private BigDecimal cashBalance() {
        return jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE is_cash_account = TRUE", BigDecimal.class);
    }

    @Test
    @DisplayName("097: Bs 15.09 en efectivo se cobra 15.10; el redondeo queda en la venta, la caja y la cuenta Caja")
    void cashSale_isRoundedUpToTheNearestTenCents() {
        BigDecimal before = cashBalance();
        SaleResponse sale = sell("15.09", PaymentMethod.EFECTIVO, null, "20.00", null);

        assertThat(sale.totalAmount()).isEqualByComparingTo("15.10");
        assertThat(sale.amountCash()).isEqualByComparingTo("15.10");
        assertThat(sale.cashRounding()).isEqualByComparingTo("0.01");
        assertThat(sale.changeGiven()).isEqualByComparingTo("4.90");
        assertThat(cashBalance()).isEqualByComparingTo(before.add(new BigDecimal("15.10")));
        CashSessionSummaryResponse session = cashSessionService.getCurrentSession();
        assertThat(session.expectedCash()).isEqualByComparingTo("115.10");
        assertThat(session.totalSales()).isEqualByComparingTo("15.10");
    }

    @Test
    @DisplayName("097: Bs 15.04 se cobra 15.00 y 15.05 se cobra 15.10 (al más cercano)")
    void cashSale_isRoundedToTheNearest() {
        SaleResponse down = sell("15.04", PaymentMethod.EFECTIVO, null, null, null);
        SaleResponse up = sell("15.05", PaymentMethod.EFECTIVO, null, null, null);

        assertThat(down.totalAmount()).isEqualByComparingTo("15.00");
        assertThat(down.cashRounding()).isEqualByComparingTo("-0.04");
        assertThat(down.amountReceived()).isEqualByComparingTo("15.00");
        assertThat(up.totalAmount()).isEqualByComparingTo("15.10");
        assertThat(up.cashRounding()).isEqualByComparingTo("0.05");
        assertThat(cashSessionService.getCurrentSession().expectedCash()).isEqualByComparingTo("130.10");
    }

    @Test
    @DisplayName("097: una venta en QR no se redondea")
    void qrSale_isNotRounded() {
        SaleResponse sale = sell("15.09", PaymentMethod.QR, null, null, null);
        assertThat(sale.totalAmount()).isEqualByComparingTo("15.09");
        assertThat(sale.cashRounding()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("097: en el pago mixto el efectivo va en múltiplos de Bs 0.10 y el QR lleva los centavos")
    void mixedSale_cashPartInTenCents() {
        SaleResponse sale = sell("15.09", PaymentMethod.MIXTO, "10.00", "10.00", null);
        assertThat(sale.totalAmount()).isEqualByComparingTo("15.09");
        assertThat(sale.amountCash()).isEqualByComparingTo("10.00");
        assertThat(sale.amountQr()).isEqualByComparingTo("5.09");

        assertThatThrownBy(() -> sell("15.09", PaymentMethod.MIXTO, "10.05", "10.05", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("múltiplos de Bs 0.10");
    }

    @Test
    @DisplayName("097: el monto recibido se compara con el efectivo redondeado")
    void received_isComparedWithTheRoundedCash() {
        assertThatThrownBy(() -> sell("15.05", PaymentMethod.EFECTIVO, null, "15.05", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no puede ser menor al total");
    }

    @Test
    @DisplayName("097: anular una venta redondeada devuelve exactamente lo que entró a la caja")
    void voidingARoundedSale_takesBackWhatCameIn() {
        BigDecimal before = cashBalance();
        SaleResponse sale = sell("15.09", PaymentMethod.EFECTIVO, null, null, null);
        saleService.voidSale(sale.id(), new VoidSaleRequest("Mal cobrada"), USER);
        next();

        assertThat(cashBalance()).isEqualByComparingTo(before);
        CashSessionSummaryResponse session = cashSessionService.getCurrentSession();
        assertThat(session.expectedCash()).isEqualByComparingTo("100.00");
        assertThat(session.totalSales()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("097, POS-18: reintentar el mismo cobro redondeado devuelve la misma venta")
    void retryingARoundedSale_returnsTheSameSale() {
        UUID requestId = UUID.randomUUID();
        UUID productId = product("15.09");
        SaleDetailRequest request = new SaleDetailRequest(List.of(new SaleItemRequest(productId, BigDecimal.ONE, null)),
            null, PaymentMethod.EFECTIVO, null, null, null, null, null, null, null, requestId);
        SaleResponse first = saleService.registerSale(request, USER);
        next();
        SaleResponse retried = saleService.registerSale(request, USER);
        next();

        assertThat(retried.id()).isEqualTo(first.id());
        assertThat(cashSessionService.getCurrentSession().totalSales()).isEqualByComparingTo("15.10");
    }
}
