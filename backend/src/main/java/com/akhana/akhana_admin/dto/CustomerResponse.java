package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Customer;
import com.akhana.akhana_admin.model.CustomerStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Customer with the receivable computed from their active credit sales and collections (DEU-05, DEU-07). */
public record CustomerResponse(
    UUID id,
    String name,
    String phone,
    String description,
    CustomerStatus status,
    BigDecimal balance,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String deletedBy,
    Instant deletedAt
) {
    public static CustomerResponse fromEntity(Customer customer, BigDecimal balance) {
        return new CustomerResponse(
            customer.getId(),
            customer.getName(),
            customer.getPhone(),
            customer.getDescription(),
            customer.getStatus(),
            balance,
            customer.getCreatedBy(),
            customer.getCreatedAt(),
            customer.getUpdatedBy(),
            customer.getUpdatedAt(),
            customer.getDeletedBy(),
            customer.getDeletedAt()
        );
    }
}
