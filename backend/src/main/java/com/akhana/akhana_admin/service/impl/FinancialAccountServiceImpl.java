package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.FinancialAccountRequest;
import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.dto.OpeningBalanceRequest;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.FinancialAccount;
import com.akhana.akhana_admin.model.FinancialTransaction;
import com.akhana.akhana_admin.model.FinancialTransactionStatus;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.FinancialAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialAccountServiceImpl implements FinancialAccountService {

    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CashSessionRepository cashSessionRepository;
    // The store's clock (America/La_Paz): decides today, the month and the day a movement counts for
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<FinancialAccountResponse> getAllAccounts() {
        return financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialAccountResponse getAccountById(UUID id) {
        FinancialAccount account = financialAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta financiera no encontrada con ID: " + id));
        return mapToResponse(account);
    }

    @Override
    @Transactional
    public FinancialAccountResponse createAccount(FinancialAccountRequest request, String currentUsername) {
        String trimmedName = request.getName().trim();
        if (financialAccountRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(trimmedName)) {
            throw new DuplicateResourceException("Ya existe una cuenta financiera con el nombre: " + trimmedName);
        }

        boolean setAsQr = Boolean.TRUE.equals(request.getIsQrAccount());
        if (setAsQr) {
            lockWithCurrentQr(null).currentQr().ifPresent(qr -> clearQrMark(qr, currentUsername));
        }

        BigDecimal initial = (request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        String color = (request.getColor() != null && !request.getColor().isBlank()) ? request.getColor() : "#2E5B27";

        FinancialAccount account = FinancialAccount.builder()
                .name(trimmedName)
                .color(color)
                .initialBalance(initial)
                .currentBalance(initial)
                .isCashAccount(false)
                .isQrAccount(setAsQr)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now(clock))
                .build();

        FinancialAccount savedAccount = financialAccountRepository.save(account);

        // The opening balance is a movement (FIN-01)
        Instant openedAt = Instant.now(clock);
        FinancialTransaction initialTx = FinancialTransaction.builder()
                .type(FinancialTransactionType.SALDO_INICIAL)
                .sourceAccount(savedAccount)
                .amount(initial)
                .transactionDate(openedAt)
                .operationDate(StoreFormat.storeDay(openedAt))
                .description("Saldo inicial de apertura")
                .status(FinancialTransactionStatus.ACTIVO)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now(clock))
                .build();
        financialTransactionRepository.save(initialTx);

        return mapToResponse(savedAccount);
    }

    @Override
    @Transactional
    public FinancialAccountResponse updateAccount(UUID id, FinancialAccountRequest request, String currentUsername) {
        // Locked so the save does not write back a balance a concurrent sale or movement already changed
        LockedAccounts locked = Boolean.TRUE.equals(request.getIsQrAccount())
                ? lockWithCurrentQr(id)
                : new LockedAccounts(lockActive(id), Optional.empty());
        FinancialAccount account = locked.target();

        String trimmedName = request.getName().trim();
        if (financialAccountRepository.existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(trimmedName, id)) {
            throw new DuplicateResourceException("Ya existe otra cuenta financiera con el nombre: " + trimmedName);
        }

        account.setName(trimmedName);
        if (request.getColor() != null && !request.getColor().isBlank()) {
            account.setColor(request.getColor());
        }

        if (request.getIsQrAccount() != null) {
            boolean requestedQr = request.getIsQrAccount();
            if (requestedQr && !Boolean.TRUE.equals(account.getIsQrAccount())) {
                locked.currentQr().ifPresent(qr -> clearQrMark(qr, currentUsername));
                account.setIsQrAccount(true);
            } else if (!requestedQr && Boolean.TRUE.equals(account.getIsQrAccount())) {
                throw new IllegalStateException("Debe existir siempre una cuenta de cobros QR. Configure otra cuenta como QR para transferir la asignación.");
            }
        }

        account.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        account.setUpdatedAt(Instant.now(clock));

        return mapToResponse(financialAccountRepository.save(account));
    }

    @Override
    @Transactional
    public FinancialAccountResponse setQrAccount(UUID id, String currentUsername) {
        LockedAccounts locked = lockWithCurrentQr(id);
        FinancialAccount targetAccount = locked.target();

        if (Boolean.TRUE.equals(targetAccount.getIsQrAccount())) {
            return mapToResponse(targetAccount);
        }

        locked.currentQr().ifPresent(qr -> clearQrMark(qr, currentUsername));

        targetAccount.setIsQrAccount(true);
        targetAccount.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        targetAccount.setUpdatedAt(Instant.now(clock));

        return mapToResponse(financialAccountRepository.save(targetAccount));
    }

    // The unique index admits one QR account at a time (FIN-03): the old mark must reach the database before the new one
    private void clearQrMark(FinancialAccount lockedQr, String currentUsername) {
        lockedQr.setIsQrAccount(false);
        lockedQr.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        lockedQr.setUpdatedAt(Instant.now(clock));
        financialAccountRepository.saveAndFlush(lockedQr);
    }

    private record LockedAccounts(FinancialAccount target, Optional<FinancialAccount> currentQr) {}

    private FinancialAccount lockActive(UUID id) {
        return financialAccountRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta financiera no encontrada con ID: " + id));
    }

    // Locks the account (if any) and the current QR account together, in id order like every money operation (T-14),
    // so neither save writes back a balance read before a concurrent sale or movement
    private LockedAccounts lockWithCurrentQr(UUID id) {
        Optional<UUID> qrId = financialAccountRepository.findCurrentQrAccountId();
        Set<UUID> ids = new HashSet<>();
        if (id != null) {
            ids.add(id);
        }
        qrId.ifPresent(ids::add);
        Map<UUID, FinancialAccount> locked = ids.isEmpty() ? Map.of()
                : financialAccountRepository.findAllByIdForUpdate(ids).stream()
                        .collect(Collectors.toMap(FinancialAccount::getId, account -> account));

        FinancialAccount target = null;
        if (id != null) {
            target = Optional.ofNullable(locked.get(id))
                    .filter(account -> account.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException("Cuenta financiera no encontrada con ID: " + id));
        }
        Optional<FinancialAccount> currentQr = qrId.map(locked::get);
        if (currentQr.isPresent() && !Boolean.TRUE.equals(currentQr.get().getIsQrAccount())) {
            throw new IllegalStateException("La cuenta de cobros QR cambió mientras se guardaba. Intente de nuevo.");
        }
        return new LockedAccounts(target, currentQr);
    }

    @Override
    @Transactional
    public FinancialAccountResponse setOpeningBalance(UUID id, OpeningBalanceRequest request, String currentUsername) {
        String username = currentUsername != null ? currentUsername : "SYSTEM";
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        FinancialAccount account = financialAccountRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta financiera no encontrada con ID: " + id));

        if (Boolean.TRUE.equals(account.getIsCashAccount()) && cashSessionRepository.existsByHistoricalImportIdIsNull()) {
            throw new IllegalStateException("Ya se abrió una caja: el saldo al corte de Caja solo se carga antes de la primera apertura.");
        }
        if (financialTransactionRepository.existsMovementsBesidesOpeningBalance(id)) {
            throw new IllegalStateException("La cuenta ya tiene movimientos: el saldo al corte solo se carga antes de operar. Corrija con un ingreso, gasto o transferencia.");
        }

        Instant now = Instant.now(clock);
        FinancialTransaction openingTx = financialTransactionRepository
                .findFirstBySourceAccountIdAndType(id, FinancialTransactionType.SALDO_INICIAL)
                .orElseGet(() -> FinancialTransaction.builder()
                        .type(FinancialTransactionType.SALDO_INICIAL)
                        .sourceAccount(account)
                        .transactionDate(now)
                        .operationDate(StoreFormat.storeDay(now))
                        .status(FinancialTransactionStatus.ACTIVO)
                        .createdBy(username)
                        .createdAt(now)
                        .build());
        openingTx.setAmount(amount);
        openingTx.setDescription("Saldo al corte");
        openingTx.setUpdatedBy(username);
        openingTx.setUpdatedAt(now);
        financialTransactionRepository.save(openingTx);

        account.setInitialBalance(amount);
        account.setCurrentBalance(amount);
        account.setUpdatedBy(username);
        account.setUpdatedAt(now);

        return mapToResponse(financialAccountRepository.save(account));
    }

    @Override
    @Transactional
    public void deleteAccount(UUID id, String currentUsername) {
        // Locked so the zero-balance check and the save see the balance after any concurrent movement
        FinancialAccount account = lockActive(id);

        if (Boolean.TRUE.equals(account.getIsCashAccount())) {
            throw new IllegalStateException("La cuenta Caja es fija del sistema y no puede ser eliminada.");
        }

        if (Boolean.TRUE.equals(account.getIsQrAccount())) {
            throw new IllegalStateException("La cuenta configurada para cobros QR no puede eliminarse mientras mantenga dicha configuración.");
        }

        if (account.getCurrentBalance() != null && account.getCurrentBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("La cuenta solo puede eliminarse cuando su saldo actual sea exactamente Bs 0,00.");
        }

        account.setDeletedAt(Instant.now(clock));
        account.setDeletedBy(currentUsername != null ? currentUsername : "SYSTEM");
        financialAccountRepository.save(account);
    }

    private boolean isOpeningBalanceEditable(FinancialAccount account) {
        if (Boolean.TRUE.equals(account.getIsCashAccount()) && cashSessionRepository.existsByHistoricalImportIdIsNull()) {
            return false;
        }
        return !financialTransactionRepository.existsMovementsBesidesOpeningBalance(account.getId());
    }

    private FinancialAccountResponse mapToResponse(FinancialAccount account) {
        return FinancialAccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .color(account.getColor())
                .initialBalance(account.getInitialBalance())
                .currentBalance(account.getCurrentBalance())
                .isCashAccount(account.getIsCashAccount())
                .isQrAccount(account.getIsQrAccount())
                .openingBalanceEditable(isOpeningBalanceEditable(account))
                .createdBy(account.getCreatedBy())
                .createdAt(account.getCreatedAt())
                .updatedBy(account.getUpdatedBy())
                .updatedAt(account.getUpdatedAt())
                .deletedBy(account.getDeletedBy())
                .deletedAt(account.getDeletedAt())
                .build();
    }
}
