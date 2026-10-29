package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.CashTransferRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.dto.PaymentAccountResponse;
import com.akhana.akhana_admin.dto.VoidFinancialTransactionRequest;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.FinancialTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialTransactionServiceImpl implements FinancialTransactionService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final CashSessionRepository cashSessionRepository;
    private final ExpectedCashCalculator expectedCashCalculator;

    static final String CASH_SESSION_REQUIRED = "Los movimientos de la cuenta Caja requieren una caja abierta.";
    static final String AMOUNT_EDIT = "El monto de un movimiento registrado no se puede editar; si hay un error, anúlelo y regístrelo de nuevo.";
    static final String ACCOUNT_EDIT = "La cuenta de un movimiento registrado no se puede editar; si hay un error, anúlelo y regístrelo de nuevo.";
    static final String CLOSED_SESSION_EDIT = "No se pueden editar movimientos de una caja cerrada; corríjalos en la caja actual.";
    static final String VOID_REASON_REQUIRED = "El motivo de anulación es obligatorio.";
    static final String INITIAL_BALANCE_VOID = "El saldo inicial de una cuenta no se puede anular.";
    static final String INITIAL_BALANCE_EDIT = "El saldo inicial de una cuenta no se puede editar.";
    static final String CLOSED_SESSION_VOID = "No se pueden anular movimientos de una caja cerrada; corríjalos en la caja actual.";
    static final String PURCHASE_MOVEMENT = "Los movimientos de un pedido se gestionan desde Compras.";
    static final String HISTORICAL_MOVEMENT = "El movimiento es histórico (migrado de la planilla): no se puede editar ni anular.";
    static final String DEBT_MOVEMENT = "Los movimientos de una deuda se gestionan desde Deudas.";
    static final String CASH_ACCOUNT_NOT_FOUND = "No se encontró la cuenta Caja en el sistema.";
    static final String CASH_TRANSFER_SAME_ACCOUNT = "Elige una cuenta distinta de Caja para el ingreso o retiro.";
    static final String CASH_TRANSFER_ONLY = "Solo se pueden anular desde el POS los ingresos y retiros de caja.";
    static final String SYSTEM_CATEGORY = "La categoría «%s» la registra el sistema (pagos de pedidos en Compras, devoluciones en Deudas, ajustes al abrir o cerrar la caja, ventas en el POS); elija otra categoría.";
    static final String CASH_ADJUSTMENT_MOVEMENT = "Los ajustes de caja se generan al abrir o cerrar la caja y no se pueden editar ni anular.";
    static final String VOID_LEAVES_NEGATIVE = "No se puede anular: la cuenta %s quedaría con saldo negativo (saldo disponible: %s). Anule primero los gastos o transferencias que usaron ese dinero.";

    @Override
    @Transactional(readOnly = true)
    public List<FinancialTransactionResponse> getTransactions(
            Instant startDate,
            Instant endDate,
            FinancialTransactionType type,
            UUID accountId,
            UUID categoryId,
            FinancialTransactionStatus status,
            String search
    ) {
        Instant effectiveStart = startDate != null ? startDate : getDefaultStartDate();
        Instant effectiveEnd = endDate != null ? endDate : getDefaultEndDate();
        String effectiveSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        return financialTransactionRepository.filterTransactions(
                effectiveStart,
                effectiveEnd,
                type,
                accountId,
                categoryId,
                status,
                effectiveSearch
        ).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialTransactionResponse> getAccountTransactions(UUID accountId, Instant startDate, Instant endDate) {
        financialAccountRepository.findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta financiera no encontrada con ID: " + accountId));

        Instant effectiveStart = startDate != null ? startDate : getDefaultStartDate();
        Instant effectiveEnd = endDate != null ? endDate : getDefaultEndDate();

        return financialTransactionRepository.findByAccountAndDateRange(accountId, effectiveStart, effectiveEnd)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialTransactionResponse> getExpensesByCashSession(UUID cashSessionId) {
        return financialTransactionRepository.findByCashSessionIdOrderByTransactionDateDesc(cashSessionId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialTransactionResponse getTransactionById(UUID id) {
        FinancialTransaction transaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción financiera no encontrada con ID: " + id));
        return mapToResponse(transaction);
    }

    @Override
    @Transactional
    public FinancialTransactionResponse createTransaction(FinancialTransactionRequest request, String currentUsername) {
        // Lock order for every money operation: cash session first, then accounts by id (T-14).
        // Movements of the Caja account need the open session and are bound to it; the client's cashSessionId is ignored (T-16)
        boolean touchesCash = isCashAccount(request.getSourceAccountId())
                || (request.getType() == FinancialTransactionType.TRANSFERENCIA && isCashAccount(request.getDestinationAccountId()));
        Optional<CashSession> lockedSession = touchesCash ? Optional.of(lockOpenSession()) : Optional.empty();
        Map<UUID, FinancialAccount> accounts = lockAccounts(request.getSourceAccountId(), request.getDestinationAccountId());

        FinancialAccount sourceAccount = activeAccount(accounts, request.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta origen no encontrada con ID: " + request.getSourceAccountId()));

        BigDecimal amount = normalizeAmount(request.getAmount());

        UUID sessionToLink = lockedSession.map(CashSession::getId).orElse(null);

        // The server dates every movement; the historical import (FIN-11) will have its own path
        Instant txDate = Instant.now();
        FinancialTransaction.FinancialTransactionBuilder builder = FinancialTransaction.builder()
                .type(request.getType())
                .sourceAccount(sourceAccount)
                .cashSessionId(sessionToLink)
                .amount(amount)
                .transactionDate(txDate)
                .description(request.getDescription().trim())
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .status(FinancialTransactionStatus.ACTIVO)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now());

        if (request.getType() == FinancialTransactionType.INGRESO_MANUAL) {
            if (Boolean.TRUE.equals(sourceAccount.getIsCashAccount())) {
                throw new IllegalStateException("Los ingresos manuales no pueden registrarse en la cuenta Caja. Los ingresos de Caja provienen exclusivamente de ventas o transferencias.");
            }
            if (request.getCategoryId() == null) {
                throw new IllegalArgumentException("La categoría es obligatoria para un ingreso manual.");
            }
            FinancialCategory category = financialCategoryRepository.findByIdAndDeletedAtIsNull(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.getCategoryId()));
            if (category.getType() != FinancialCategoryType.INGRESO) {
                throw new IllegalArgumentException("La categoría para un ingreso debe ser de tipo INGRESO.");
            }
            requireManualCategory(category);
            builder.category(category);

            // Apply to the balance
            sourceAccount.setCurrentBalance(sourceAccount.getCurrentBalance().add(amount));
            financialAccountRepository.save(sourceAccount);

        } else if (request.getType() == FinancialTransactionType.GASTO_MANUAL) {
            if (request.getCategoryId() == null) {
                throw new IllegalArgumentException("La categoría es obligatoria para un gasto manual.");
            }
            FinancialCategory category = financialCategoryRepository.findByIdAndDeletedAtIsNull(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.getCategoryId()));
            if (category.getType() != FinancialCategoryType.GASTO) {
                throw new IllegalArgumentException("La categoría para un gasto debe ser de tipo GASTO.");
            }
            requireManualCategory(category);
            builder.category(category);

            // Apply to the balance
            sourceAccount.setCurrentBalance(sourceAccount.getCurrentBalance().subtract(amount));
            financialAccountRepository.save(sourceAccount);

        } else if (request.getType() == FinancialTransactionType.TRANSFERENCIA) {
            if (request.getDestinationAccountId() == null) {
                throw new IllegalArgumentException("La cuenta destino es obligatoria para una transferencia.");
            }
            if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
                throw new IllegalArgumentException("La cuenta origen y la cuenta destino deben ser diferentes.");
            }
            FinancialAccount destinationAccount = activeAccount(accounts, request.getDestinationAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cuenta destino no encontrada con ID: " + request.getDestinationAccountId()));

            // Enough balance in the source account
            if (sourceAccount.getCurrentBalance().compareTo(amount) < 0) {
                throw new IllegalStateException(String.format(
                        "Saldo insuficiente en la cuenta origen (%s). Saldo disponible: %s; monto a transferir: %s.",
                        sourceAccount.getName(), StoreFormat.money(sourceAccount.getCurrentBalance()), StoreFormat.money(amount)
                ));
            }

            builder.destinationAccount(destinationAccount);

            // Apply to both balances
            sourceAccount.setCurrentBalance(sourceAccount.getCurrentBalance().subtract(amount));
            destinationAccount.setCurrentBalance(destinationAccount.getCurrentBalance().add(amount));
            financialAccountRepository.save(sourceAccount);
            financialAccountRepository.save(destinationAccount);

        } else {
            throw new IllegalArgumentException("Tipo de transacción no admitido para registro manual.");
        }

        FinancialTransaction savedTx = financialTransactionRepository.save(builder.build());

        lockedSession.ifPresent(this::refreshExpectedCash);

        return mapToResponse(savedTx);
    }

    @Override
    @Transactional
    public FinancialTransactionResponse updateTransaction(UUID id, FinancialTransactionRequest request, String currentUsername) {
        FinancialTransaction tx = financialTransactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción financiera no encontrada con ID: " + id));

        if (tx.getSaleId() != null) {
            throw new IllegalStateException("Los movimientos financieros originados por ventas no pueden editarse desde este módulo; se gestionan exclusivamente desde Ventas.");
        }

        if (tx.getPurchaseId() != null) {
            throw new IllegalStateException(PURCHASE_MOVEMENT);
        }

        if (tx.isHistorical()) {
            throw new IllegalStateException(HISTORICAL_MOVEMENT);
        }

        if (tx.getCreditorRepaymentId() != null || tx.getCustomerCollectionId() != null) {
            throw new IllegalStateException(DEBT_MOVEMENT);
        }

        if (tx.getStatus() == FinancialTransactionStatus.ANULADO) {
            throw new IllegalStateException("No se puede editar una transacción que ya ha sido anulada.");
        }

        if (tx.getType() == FinancialTransactionType.TRANSFERENCIA) {
            throw new IllegalStateException("Las transferencias entre cuentas no pueden editarse; en caso de error deben anularse y registrarse nuevamente.");
        }

        if (tx.getType() == FinancialTransactionType.SALDO_INICIAL) {
            throw new IllegalStateException(INITIAL_BALANCE_EDIT);
        }

        if (isCashAdjustment(tx)) {
            throw new IllegalStateException(CASH_ADJUSTMENT_MOVEMENT);
        }

        if (tx.getType() != request.getType()) {
            throw new IllegalArgumentException("No se puede cambiar el tipo de una transacción ya registrada.");
        }

        // Money movements are immutable (T-05): amount and account are fixed once registered
        if (normalizeAmount(request.getAmount()).compareTo(tx.getAmount()) != 0) {
            throw new IllegalArgumentException(AMOUNT_EDIT);
        }
        if (!tx.getSourceAccount().getId().equals(request.getSourceAccountId())) {
            throw new IllegalArgumentException(ACCOUNT_EDIT);
        }

        // A movement of a closed session is immutable (CAJ-06); same lock order as T-14: movement, then session
        if (tx.getCashSessionId() != null) {
            lockSessionIfOpen(tx.getCashSessionId(), CLOSED_SESSION_EDIT);
        }

        if (request.getCategoryId() != null) {
            FinancialCategory category = financialCategoryRepository.findByIdAndDeletedAtIsNull(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.getCategoryId()));
            if (request.getType() == FinancialTransactionType.INGRESO_MANUAL && category.getType() != FinancialCategoryType.INGRESO) {
                throw new IllegalArgumentException("La categoría para un ingreso debe ser de tipo INGRESO.");
            }
            if (request.getType() == FinancialTransactionType.GASTO_MANUAL && category.getType() != FinancialCategoryType.GASTO) {
                throw new IllegalArgumentException("La categoría para un gasto debe ser de tipo GASTO.");
            }
            // An old movement may keep its category; it cannot move to one the system records by itself
            if (tx.getCategory() == null || !tx.getCategory().getId().equals(category.getId())) {
                requireManualCategory(category);
            }
            tx.setCategory(category);
        }

        tx.setDescription(request.getDescription().trim());
        tx.setNotes(request.getNotes() != null ? request.getNotes().trim() : null);
        tx.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        tx.setUpdatedAt(Instant.now());

        FinancialTransaction updatedTx = financialTransactionRepository.save(tx);

        return mapToResponse(updatedTx);
    }

    @Override
    @Transactional
    public FinancialTransactionResponse voidCashExpense(UUID id, VoidFinancialTransactionRequest request, String currentUsername) {
        requireVoidReason(request);
        FinancialTransaction tx = financialTransactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción financiera no encontrada con ID: " + id));

        // The POS screen only voids its own cash expenses; any other movement is voided from Finanzas
        if (tx.getType() != FinancialTransactionType.GASTO_MANUAL || tx.getCashSessionId() == null) {
            throw new IllegalStateException("Solo se pueden anular desde el POS los gastos de caja.");
        }
        if (tx.getPurchaseId() != null) {
            throw new IllegalStateException(PURCHASE_MOVEMENT);
        }

        if (tx.isHistorical()) {
            throw new IllegalStateException(HISTORICAL_MOVEMENT);
        }

        if (tx.getCreditorRepaymentId() != null || tx.getCustomerCollectionId() != null) {
            throw new IllegalStateException(DEBT_MOVEMENT);
        }
        lockSessionIfOpen(tx.getCashSessionId(), "No se pueden anular gastos de una caja cerrada.");

        return voidTransaction(id, request, currentUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAccountResponse> getCashTransferAccounts() {
        return financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsCashAccount()))
                .map(a -> new PaymentAccountResponse(a.getId(), a.getName(), a.getColor(), false,
                        Boolean.TRUE.equals(a.getIsQrAccount())))
                .toList();
    }

    @Override
    @Transactional
    public FinancialTransactionResponse createCashTransfer(CashTransferRequest request, String currentUsername) {
        FinancialAccount cashAccount = financialAccountRepository.findByIsCashAccountTrue()
                .orElseThrow(() -> new ResourceNotFoundException(CASH_ACCOUNT_NOT_FOUND));
        if (cashAccount.getId().equals(request.getAccountId())) {
            throw new IllegalArgumentException(CASH_TRANSFER_SAME_ACCOUNT);
        }
        FinancialAccount otherAccount = financialAccountRepository.findByIdAndDeletedAtIsNull(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + request.getAccountId()));

        boolean withdrawal = request.getDirection() == CashTransferDirection.RETIRO;
        FinancialTransactionRequest txRequest = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccountId(withdrawal ? cashAccount.getId() : otherAccount.getId())
                .destinationAccountId(withdrawal ? otherAccount.getId() : cashAccount.getId())
                .amount(request.getAmount())
                .description(withdrawal
                        ? "Retiro de caja a " + otherAccount.getName()
                        : "Ingreso a caja desde " + otherAccount.getName())
                .notes(request.getComment())
                .build();

        // createTransaction requires the open session, binds the transfer to it and refreshes the expected cash
        return createTransaction(txRequest, currentUsername);
    }

    @Override
    @Transactional
    public FinancialTransactionResponse voidCashTransfer(UUID id, VoidFinancialTransactionRequest request, String currentUsername) {
        requireVoidReason(request);
        FinancialTransaction tx = financialTransactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción financiera no encontrada con ID: " + id));

        // Only transfers bound to a cash session move money in or out of the drawer
        if (tx.getType() != FinancialTransactionType.TRANSFERENCIA || tx.getCashSessionId() == null) {
            throw new IllegalStateException(CASH_TRANSFER_ONLY);
        }
        lockSessionIfOpen(tx.getCashSessionId(), "No se pueden anular ingresos o retiros de una caja cerrada.");

        return voidTransaction(id, request, currentUsername);
    }

    @Override
    @Transactional
    public FinancialTransactionResponse voidTransaction(UUID id, VoidFinancialTransactionRequest request, String currentUsername) {
        String voidReason = requireVoidReason(request);
        FinancialTransaction tx = financialTransactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción financiera no encontrada con ID: " + id));

        if (tx.getStatus() == FinancialTransactionStatus.ANULADO) {
            throw new IllegalStateException("La transacción ya se encuentra anulada.");
        }

        if (tx.getSaleId() != null) {
            throw new IllegalStateException("Las transacciones generadas por ventas no pueden anularse desde Finanzas. Deben anularse desde el módulo de Ventas.");
        }

        if (tx.getPurchaseId() != null) {
            throw new IllegalStateException(PURCHASE_MOVEMENT);
        }

        if (tx.isHistorical()) {
            throw new IllegalStateException(HISTORICAL_MOVEMENT);
        }

        if (tx.getCreditorRepaymentId() != null || tx.getCustomerCollectionId() != null) {
            throw new IllegalStateException(DEBT_MOVEMENT);
        }

        if (tx.getType() == FinancialTransactionType.SALDO_INICIAL) {
            throw new IllegalStateException(INITIAL_BALANCE_VOID);
        }

        if (isCashAdjustment(tx)) {
            throw new IllegalStateException(CASH_ADJUSTMENT_MOVEMENT);
        }

        Optional<CashSession> lockedSession = tx.getCashSessionId() != null
                ? Optional.of(lockSessionIfOpen(tx.getCashSessionId(), CLOSED_SESSION_VOID))
                : Optional.empty();
        lockAccounts(
                tx.getSourceAccount() != null ? tx.getSourceAccount().getId() : null,
                tx.getDestinationAccount() != null ? tx.getDestinationAccount().getId() : null);

        // Revert the effect on the balances
        BigDecimal amount = tx.getAmount();
        FinancialAccount source = tx.getSourceAccount();

        // Like a transfer (FIN-06), reverting money that already left the account cannot leave it negative
        if (tx.getType() == FinancialTransactionType.INGRESO_MANUAL) {
            requireBalanceFor(source, amount);
        } else if (tx.getType() == FinancialTransactionType.TRANSFERENCIA) {
            requireBalanceFor(tx.getDestinationAccount(), amount);
        }

        if (tx.getType() == FinancialTransactionType.INGRESO_MANUAL || tx.getType() == FinancialTransactionType.VENTA_INGRESO) {
            source.setCurrentBalance(source.getCurrentBalance().subtract(amount));
            financialAccountRepository.save(source);
        } else if (tx.getType() == FinancialTransactionType.GASTO_MANUAL) {
            source.setCurrentBalance(source.getCurrentBalance().add(amount));
            financialAccountRepository.save(source);
        } else if (tx.getType() == FinancialTransactionType.TRANSFERENCIA) {
            FinancialAccount destination = tx.getDestinationAccount();
            source.setCurrentBalance(source.getCurrentBalance().add(amount));
            destination.setCurrentBalance(destination.getCurrentBalance().subtract(amount));
            financialAccountRepository.save(source);
            financialAccountRepository.save(destination);
        }

        tx.setStatus(FinancialTransactionStatus.ANULADO);
        tx.setVoidedAt(Instant.now());
        tx.setVoidedBy(currentUsername != null ? currentUsername : "SYSTEM");
        tx.setVoidReason(voidReason);

        FinancialTransaction voidedTx = financialTransactionRepository.save(tx);

        lockedSession.ifPresent(this::refreshExpectedCash);

        return mapToResponse(voidedTx);
    }

    // Amounts are stored with scale 2 HALF_UP; one that rounds to 0.00 is rejected
    static BigDecimal normalizeAmount(BigDecimal amount) {
        BigDecimal normalized = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : null;
        if (normalized == null || normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a Bs 0,00.");
        }
        return normalized;
    }

    // Every void needs a reason (RG-03), also when the request has no body
    private static String requireVoidReason(VoidFinancialTransactionRequest request) {
        if (request == null || request.getVoidReason() == null || request.getVoidReason().isBlank()) {
            throw new IllegalArgumentException(VOID_REASON_REQUIRED);
        }
        return request.getVoidReason().trim();
    }

    private static void requireManualCategory(FinancialCategory category) {
        if (!category.isManualEntryAllowed()) {
            throw new IllegalArgumentException(String.format(SYSTEM_CATEGORY, category.getName()));
        }
    }

    private static boolean isCashAdjustment(FinancialTransaction tx) {
        return tx.getCategory() != null && tx.getCategory().isCashAdjustment();
    }

    private static void requireBalanceFor(FinancialAccount account, BigDecimal amount) {
        if (account.getCurrentBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(String.format(VOID_LEAVES_NEGATIVE, account.getName(),
                    StoreFormat.money(account.getCurrentBalance())));
        }
    }

    private boolean isCashAccount(UUID accountId) {
        return accountId != null && financialAccountRepository.existsByIdAndIsCashAccountTrue(accountId);
    }

    private CashSession lockOpenSession() {
        return cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CASH_SESSION_REQUIRED));
    }

    private CashSession lockSessionIfOpen(UUID sessionId, String closedMessage) {
        return cashSessionRepository.findByIdForUpdate(sessionId)
                .filter(session -> session.getStatus() == CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(closedMessage));
    }

    // Flushes first so the repository sums see the movement just saved
    private void refreshExpectedCash(CashSession session) {
        financialTransactionRepository.flush();
        expectedCashCalculator.refresh(session);
        cashSessionRepository.save(session);
    }

    // Locked accounts are loaded fresh into the persistence context, so lazy references to them see the locked state
    private Map<UUID, FinancialAccount> lockAccounts(UUID... ids) {
        Set<UUID> idSet = Arrays.stream(ids).filter(Objects::nonNull).collect(Collectors.toSet());
        if (idSet.isEmpty()) {
            return Map.of();
        }
        return financialAccountRepository.findAllByIdForUpdate(idSet).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, account -> account));
    }

    private Optional<FinancialAccount> activeAccount(Map<UUID, FinancialAccount> accounts, UUID id) {
        return Optional.ofNullable(id != null ? accounts.get(id) : null)
                .filter(account -> account.getDeletedAt() == null);
    }

    private Instant getDefaultStartDate() {
        return YearMonth.now(StoreFormat.STORE_ZONE).atDay(1).atStartOfDay(StoreFormat.STORE_ZONE).toInstant();
    }

    private Instant getDefaultEndDate() {
        return LocalDate.now(StoreFormat.STORE_ZONE).atTime(23, 59, 59, 999_999_999).atZone(StoreFormat.STORE_ZONE).toInstant();
    }

    private FinancialTransactionResponse mapToResponse(FinancialTransaction tx) {
        FinancialTransactionResponse.AccountSummary sourceSummary = null;
        if (tx.getSourceAccount() != null) {
            sourceSummary = FinancialTransactionResponse.AccountSummary.builder()
                    .id(tx.getSourceAccount().getId())
                    .name(tx.getSourceAccount().getName())
                    .color(tx.getSourceAccount().getColor())
                    .cashAccount(Boolean.TRUE.equals(tx.getSourceAccount().getIsCashAccount()))
                    .build();
        }

        FinancialTransactionResponse.AccountSummary destSummary = null;
        if (tx.getDestinationAccount() != null) {
            destSummary = FinancialTransactionResponse.AccountSummary.builder()
                    .id(tx.getDestinationAccount().getId())
                    .name(tx.getDestinationAccount().getName())
                    .color(tx.getDestinationAccount().getColor())
                    .cashAccount(Boolean.TRUE.equals(tx.getDestinationAccount().getIsCashAccount()))
                    .build();
        }

        FinancialTransactionResponse.CategorySummary catSummary = null;
        if (tx.getCategory() != null) {
            catSummary = FinancialTransactionResponse.CategorySummary.builder()
                    .id(tx.getCategory().getId())
                    .name(tx.getCategory().getName())
                    .type(tx.getCategory().getType())
                    .color(tx.getCategory().getColor())
                    .build();
        }

        return FinancialTransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .transactionDate(tx.getTransactionDate())
                .description(tx.getDescription())
                .notes(tx.getNotes())
                .status(tx.getStatus())
                .saleId(tx.getSaleId())
                .purchaseId(tx.getPurchaseId())
                .creditorRepaymentId(tx.getCreditorRepaymentId())
                .customerCollectionId(tx.getCustomerCollectionId())
                .historical(tx.isHistorical())
                .cashSessionId(tx.getCashSessionId())
                .sourceAccount(sourceSummary)
                .destinationAccount(destSummary)
                .category(catSummary)
                .createdBy(tx.getCreatedBy())
                .createdAt(tx.getCreatedAt())
                .updatedBy(tx.getUpdatedBy())
                .updatedAt(tx.getUpdatedAt())
                .voidedBy(tx.getVoidedBy())
                .voidedAt(tx.getVoidedAt())
                .voidReason(tx.getVoidReason())
                .build();
    }
}
