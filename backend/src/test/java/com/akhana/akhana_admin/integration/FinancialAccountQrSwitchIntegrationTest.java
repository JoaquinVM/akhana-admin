package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.FinancialAccountRequest;
import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.model.FinancialAccount;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.service.FinancialAccountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FIN-03 against the real unique index of one QR account: a mock cannot see the order in which Hibernate sends the
 * changes. Each flush sends them as a commit would, and the test transaction is rolled back at the end: nothing stays.
 */
@SpringBootTest
@Transactional
class FinancialAccountQrSwitchIntegrationTest {

    private static final String ADMIN = "admin-qr-switch";

    @Autowired private FinancialAccountService financialAccountService;
    @Autowired private FinancialAccountRepository financialAccountRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private UUID previousQrId;
    private BigDecimal previousQrBalance;

    @BeforeEach
    void rememberCurrentQrAccount() {
        FinancialAccount previousQr = financialAccountRepository.findByIsQrAccountTrueAndDeletedAtIsNull().orElseThrow();
        previousQrId = previousQr.getId();
        previousQrBalance = previousQr.getCurrentBalance();
    }

    @Test
    @DisplayName("Crear una cuenta marcada QR la deja como cuenta QR y le quita la marca a la anterior")
    void createMarkedAsQr_movesTheMark() {
        FinancialAccountResponse created = financialAccountService.createAccount(request("QR nueva", true), ADMIN);
        sendToDatabase();

        assertOnlyQrAccountIs(created.getId());
        assertPreviousQrLostTheMarkKeepingItsBalance();
        assertThat(reload(created.getId()).getCurrentBalance()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("Editar otra cuenta marcándola QR le pasa la marca")
    void updateMarkedAsQr_movesTheMark() {
        UUID otherId = otherAccount();

        financialAccountService.updateAccount(otherId, request("Otra cuenta editada", true), ADMIN);
        sendToDatabase();

        assertOnlyQrAccountIs(otherId);
        assertPreviousQrLostTheMarkKeepingItsBalance();
        assertThat(reload(otherId).getName()).isEqualTo("Otra cuenta editada");
        assertThat(reload(otherId).getCurrentBalance()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("El cambio directo de cuenta QR le pasa la marca")
    void setQrAccount_movesTheMark() {
        UUID otherId = otherAccount();

        financialAccountService.setQrAccount(otherId, ADMIN);
        sendToDatabase();

        assertOnlyQrAccountIs(otherId);
        assertPreviousQrLostTheMarkKeepingItsBalance();
        assertThat(reload(otherId).getCurrentBalance()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("Desmarcar la cuenta QR actual sigue rechazado y ella conserva la marca")
    void unmarkCurrentQr_isRejected() {
        String name = reload(previousQrId).getName();

        assertThatThrownBy(() -> financialAccountService.updateAccount(previousQrId, request(name, false), ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Debe existir siempre una cuenta de cobros QR");

        entityManager.clear();
        assertOnlyQrAccountIs(previousQrId);
    }

    @Test
    @DisplayName("Crear o editar sin marcar QR no toca la cuenta QR")
    void withoutTheMark_qrAccountStaysTheSame() {
        UUID otherId = otherAccount();

        financialAccountService.updateAccount(otherId, request("Otra cuenta editada", false), ADMIN);
        sendToDatabase();

        assertOnlyQrAccountIs(previousQrId);
        assertThat(reload(previousQrId).getUpdatedBy()).isNotEqualTo(ADMIN);
    }

    private UUID otherAccount() {
        UUID id = financialAccountService.createAccount(request("Otra cuenta", false), ADMIN).getId();
        sendToDatabase();
        return id;
    }

    private FinancialAccountRequest request(String name, boolean isQr) {
        FinancialAccountRequest request = new FinancialAccountRequest();
        request.setName(name);
        request.setColor("#123456");
        request.setInitialBalance(new BigDecimal("25.00"));
        request.setIsQrAccount(isQr);
        return request;
    }

    private void sendToDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    private FinancialAccount reload(UUID id) {
        return financialAccountRepository.findById(id).orElseThrow();
    }

    private void assertOnlyQrAccountIs(UUID id) {
        assertThat(jdbc.queryForList(
                "SELECT id FROM financial_accounts WHERE is_qr_account = TRUE AND deleted_at IS NULL", UUID.class))
                .containsExactly(id);
    }

    private void assertPreviousQrLostTheMarkKeepingItsBalance() {
        FinancialAccount previousQr = reload(previousQrId);
        assertThat(previousQr.getIsQrAccount()).isFalse();
        assertThat(previousQr.getUpdatedBy()).isEqualTo(ADMIN);
        assertThat(previousQr.getUpdatedAt()).isNotNull();
        assertThat(previousQr.getCurrentBalance()).isEqualByComparingTo(previousQrBalance);
    }
}
