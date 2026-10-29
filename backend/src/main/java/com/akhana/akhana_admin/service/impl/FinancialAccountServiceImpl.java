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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialAccountServiceImpl implements FinancialAccountService {

    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CashSessionRepository cashSessionRepository;

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
            financialAccountRepository.findByIsQrAccountTrueAndDeletedAtIsNull()
                    .ifPresent(existingQr -> {
                        existingQr.setIsQrAccount(false);
                        financialAccountRepository.save(existingQr);
                    });
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
                .createdAt(Instant.now())
                .build();

        FinancialAccount savedAccount = financialAccountRepository.save(account);

        // Registrar movimiento de saldo inicial
        FinancialTransaction initialTx = FinancialTransaction.builder()
                .type(FinancialTransactionType.SALDO_INICIAL)
                .sourceAccount(savedAccount)
                .amount(initial)
                .transactionDate(Instant.now())
                .description("Saldo inicial de apertura")
                .status(FinancialTransactionStatus.ACTIVO)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now())
                .build();
        financialTransactionRepository.save(initialTx);

        return mapToResponse(savedAccount);
    }

    @Override
    @Transactional
    public FinancialAccountResponse updateAccount(UUID id, FinancialAccountRequest request, String currentUsername) {
        FinancialAccount account = financialAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta financiera no encontrada con ID: " + id));

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
                financialAccountRepository.findByIsQrAccountTrueAndDeletedAtIsNull()
                        .ifPresent(existingQr -> {
                            existingQr.setIsQrAccount(false);
                            financialAccountRepository.save(existingQr);
                        });
                account.setIsQrAccount(true);
            } else if (!requestedQr && Boolean.TRUE.equals(account.getIsQrAccount())) {
                throw new IllegalStateException("Debe existir siempre una cuenta de cobros QR. Configure otra cuenta como QR para transferir la asignación.");
            }
        }

        account.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        account.setUpdatedAt(Instant.now());

        return mapToResponse(financialAccountRepository.save(account));
    }

    @Override
    @Transactional
    public FinancialAccountResponse setQrAccount(UUID id, String currentUsername) {
        FinancialAccount targetAccount = financialAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta financiera no encontrada con ID: " + id));

        if (Boolean.TRUE.equals(targetAccount.getIsQrAccount())) {
            return mapToResponse(targetAccount);
        }

        financialAccountRepository.findByIsQrAccountTrueAndDeletedAtIsNull()
                .ifPresent(existingQr -> {
                    existingQr.setIsQrAccount(false);
                    existingQr.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
                    existingQr.setUpdatedAt(Instant.now());
                    financialAccountRepository.save(existingQr);
                });

        targetAccount.setIsQrAccount(true);
        targetAccount.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        targetAccount.setUpdatedAt(Instant.now());

        return mapToResponse(financialAccountRepository.save(targetAccount));
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

        Instant now = Instant.now();
        FinancialTransaction openingTx = financialTransactionRepository
                .findFirstBySourceAccountIdAndType(id, FinancialTransactionType.SALDO_INICIAL)
                .orElseGet(() -> FinancialTransaction.builder()
                        .type(FinancialTransactionType.SALDO_INICIAL)
                        .sourceAccount(account)
                        .transactionDate(now)
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
        FinancialAccount account = financialAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta financiera no encontrada con ID: " + id));

        if (Boolean.TRUE.equals(account.getIsCashAccount())) {
            throw new IllegalStateException("La cuenta Caja es fija del sistema y no puede ser eliminada.");
        }

        if (Boolean.TRUE.equals(account.getIsQrAccount())) {
            throw new IllegalStateException("La cuenta configurada para cobros QR no puede eliminarse mientras mantenga dicha configuración.");
        }

        if (account.getCurrentBalance() != null && account.getCurrentBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("La cuenta solo puede eliminarse cuando su saldo actual sea exactamente 0.00 Bs.");
        }

        account.setDeletedAt(Instant.now());
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
