package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
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
    static final String CLOSED_SESSION_EDIT = "No se pueden editar movimientos de una caja cerrada; corríjalos en la caja actual.";
    static final String CLOSED_SESSION_VOID = "No se pueden anular movimientos de una caja cerrada; corríjalos en la caja actual.";

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

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser estrictamente mayor a 0.00 Bs.");
        }

        UUID sessionToLink = lockedSession.map(CashSession::getId).orElse(null);

        Instant txDate = request.getTransactionDate() != null ? request.getTransactionDate() : Instant.now();
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
            builder.category(category);

            // Afectar saldo
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
            builder.category(category);

            // Afectar saldo
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

            // Validar saldo suficiente
            if (sourceAccount.getCurrentBalance().compareTo(amount) < 0) {
                throw new IllegalStateException(String.format(
                        "Saldo insuficiente en la cuenta origen (%s). Saldo disponible: %.2f Bs, monto a transferir: %.2f Bs.",
                        sourceAccount.getName(), sourceAccount.getCurrentBalance(), amount
                ));
            }

            builder.destinationAccount(destinationAccount);

            // Afectar ambos saldos
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

        if (tx.getStatus() == FinancialTransactionStatus.ANULADO) {
            throw new IllegalStateException("No se puede editar una transacción que ya ha sido anulada.");
        }

        if (tx.getType() == FinancialTransactionType.TRANSFERENCIA) {
            throw new IllegalStateException("Las transferencias entre cuentas no pueden editarse; en caso de error deben anularse y registrarse nuevamente.");
        }

        if (tx.getType() != request.getType()) {
            throw new IllegalArgumentException("No se puede cambiar el tipo de una transacción ya registrada.");
        }

        BigDecimal newAmount = request.getAmount();
        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser estrictamente mayor a 0.00 Bs.");
        }

        // A movement of a closed session is immutable (CAJ-06); one moved onto Caja is bound to the open session (T-16)
        Optional<CashSession> lockedSession;
        if (tx.getCashSessionId() != null) {
            lockedSession = Optional.of(lockSessionIfOpen(tx.getCashSessionId(), CLOSED_SESSION_EDIT));
        } else {
            lockedSession = isCashAccount(request.getSourceAccountId()) ? Optional.of(lockOpenSession()) : Optional.empty();
        }
        UUID currentSourceId = tx.getSourceAccount().getId();
        Map<UUID, FinancialAccount> accounts = lockAccounts(currentSourceId, request.getSourceAccountId());
        FinancialAccount currentSource = accounts.get(currentSourceId);
        FinancialAccount targetSource = currentSource;

        if (!currentSource.getId().equals(request.getSourceAccountId())) {
            targetSource = activeAccount(accounts, request.getSourceAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Nueva cuenta origen no encontrada con ID: " + request.getSourceAccountId()));
        }

        // Revertir impacto del monto anterior
        if (tx.getType() == FinancialTransactionType.INGRESO_MANUAL) {
            currentSource.setCurrentBalance(currentSource.getCurrentBalance().subtract(tx.getAmount()));
        } else if (tx.getType() == FinancialTransactionType.GASTO_MANUAL) {
            currentSource.setCurrentBalance(currentSource.getCurrentBalance().add(tx.getAmount()));
        }

        // Aplicar nuevo impacto
        if (request.getType() == FinancialTransactionType.INGRESO_MANUAL) {
            if (Boolean.TRUE.equals(targetSource.getIsCashAccount())) {
                throw new IllegalStateException("Los ingresos manuales no pueden registrarse en la cuenta Caja.");
            }
            targetSource.setCurrentBalance(targetSource.getCurrentBalance().add(newAmount));
        } else if (request.getType() == FinancialTransactionType.GASTO_MANUAL) {
            targetSource.setCurrentBalance(targetSource.getCurrentBalance().subtract(newAmount));
        }

        if (!currentSource.getId().equals(targetSource.getId())) {
            financialAccountRepository.save(currentSource);
        }
        financialAccountRepository.save(targetSource);

        if (request.getCategoryId() != null) {
            FinancialCategory category = financialCategoryRepository.findByIdAndDeletedAtIsNull(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.getCategoryId()));
            if (request.getType() == FinancialTransactionType.INGRESO_MANUAL && category.getType() != FinancialCategoryType.INGRESO) {
                throw new IllegalArgumentException("La categoría para un ingreso debe ser de tipo INGRESO.");
            }
            if (request.getType() == FinancialTransactionType.GASTO_MANUAL && category.getType() != FinancialCategoryType.GASTO) {
                throw new IllegalArgumentException("La categoría para un gasto debe ser de tipo GASTO.");
            }
            tx.setCategory(category);
        }

        tx.setSourceAccount(targetSource);
        tx.setCashSessionId(Boolean.TRUE.equals(targetSource.getIsCashAccount())
                ? lockedSession.map(CashSession::getId).orElse(null)
                : null);
        tx.setAmount(newAmount);
        if (request.getTransactionDate() != null) {
            tx.setTransactionDate(request.getTransactionDate());
        }
        tx.setDescription(request.getDescription().trim());
        tx.setNotes(request.getNotes() != null ? request.getNotes().trim() : null);
        tx.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        tx.setUpdatedAt(Instant.now());

        FinancialTransaction updatedTx = financialTransactionRepository.save(tx);

        lockedSession.ifPresent(this::refreshExpectedCash);

        return mapToResponse(updatedTx);
    }

    @Override
    @Transactional
    public FinancialTransactionResponse voidCashExpense(UUID id, VoidFinancialTransactionRequest request, String currentUsername) {
        FinancialTransaction tx = financialTransactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción financiera no encontrada con ID: " + id));

        // The POS screen only voids its own cash expenses; any other movement is voided from Finanzas
        if (tx.getType() != FinancialTransactionType.GASTO_MANUAL || tx.getCashSessionId() == null) {
            throw new IllegalStateException("Solo se pueden anular desde el POS los gastos de caja.");
        }
        lockSessionIfOpen(tx.getCashSessionId(), "No se pueden anular gastos de una caja cerrada.");

        return voidTransaction(id, request, currentUsername);
    }

    @Override
    @Transactional
    public FinancialTransactionResponse voidTransaction(UUID id, VoidFinancialTransactionRequest request, String currentUsername) {
        FinancialTransaction tx = financialTransactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción financiera no encontrada con ID: " + id));

        if (tx.getStatus() == FinancialTransactionStatus.ANULADO) {
            throw new IllegalStateException("La transacción ya se encuentra anulada.");
        }

        if (tx.getSaleId() != null) {
            throw new IllegalStateException("Las transacciones generadas por ventas no pueden anularse desde Finanzas. Deben anularse desde el módulo de Ventas.");
        }

        Optional<CashSession> lockedSession = tx.getCashSessionId() != null
                ? Optional.of(lockSessionIfOpen(tx.getCashSessionId(), CLOSED_SESSION_VOID))
                : Optional.empty();
        lockAccounts(
                tx.getSourceAccount() != null ? tx.getSourceAccount().getId() : null,
                tx.getDestinationAccount() != null ? tx.getDestinationAccount().getId() : null);

        // Revertir impacto en los saldos
        BigDecimal amount = tx.getAmount();
        FinancialAccount source = tx.getSourceAccount();

        if (tx.getType() == FinancialTransactionType.INGRESO_MANUAL || tx.getType() == FinancialTransactionType.VENTA_INGRESO || tx.getType() == FinancialTransactionType.SALDO_INICIAL) {
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
        tx.setVoidReason(request != null && request.getVoidReason() != null ? request.getVoidReason().trim() : null);

        FinancialTransaction voidedTx = financialTransactionRepository.save(tx);

        lockedSession.ifPresent(this::refreshExpectedCash);

        return mapToResponse(voidedTx);
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
        return YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant getDefaultEndDate() {
        return LocalDate.now().atTime(23, 59, 59, 999_999_999).atZone(ZoneId.systemDefault()).toInstant();
    }

    private FinancialTransactionResponse mapToResponse(FinancialTransaction tx) {
        FinancialTransactionResponse.AccountSummary sourceSummary = null;
        if (tx.getSourceAccount() != null) {
            sourceSummary = FinancialTransactionResponse.AccountSummary.builder()
                    .id(tx.getSourceAccount().getId())
                    .name(tx.getSourceAccount().getName())
                    .color(tx.getSourceAccount().getColor())
                    .build();
        }

        FinancialTransactionResponse.AccountSummary destSummary = null;
        if (tx.getDestinationAccount() != null) {
            destSummary = FinancialTransactionResponse.AccountSummary.builder()
                    .id(tx.getDestinationAccount().getId())
                    .name(tx.getDestinationAccount().getName())
                    .color(tx.getDestinationAccount().getColor())
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
