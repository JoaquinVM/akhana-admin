package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;

// CAJ-10: proposed opening amount, taken from the last closed session (0.00 and no number when there is none)
public record CashOpeningProposalResponse(
    BigDecimal proposedAmount,
    Long previousSessionNumber
) {}
