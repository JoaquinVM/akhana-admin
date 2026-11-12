package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A debt paid by a repayment (DEU-03): a purchase payment or a shipping paid by the person (DEU-02), or a sale of
 * their products (POS-13). Exactly one of the three. It counts while its repayment is active.
 */
@Entity
@Table(name = "creditor_repayment_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditorRepaymentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repayment_id", nullable = false, updatable = false)
    private CreditorRepayment repayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_payment_id", updatable = false)
    private PurchasePayment purchasePayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_charge_id", updatable = false)
    private PurchaseShippingCharge shippingCharge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", updatable = false)
    private Sale sale;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    /** Id of the debt it pays, whatever its kind. */
    public UUID debtId() {
        if (purchasePayment != null) return purchasePayment.getId();
        if (shippingCharge != null) return shippingCharge.getId();
        return sale.getId();
    }
}
