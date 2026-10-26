package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;

// CAJ-10: proposed opening amount, taken from the last closed session (the Caja balance and no number when there is none, FIN-01)
public record CashOpeningProposalResponse(
    BigDecimal proposedAmount,
    Long previousSessionNumber
) {}
