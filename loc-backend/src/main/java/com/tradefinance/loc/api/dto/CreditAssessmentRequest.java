package com.tradefinance.loc.api.dto;

import java.math.BigDecimal;

/** Sent by the REST connector in BPMN task "Perform credit assessment". */
public record CreditAssessmentRequest(
        String applicationId,
        String customerId,
        BigDecimal amount,
        String currency,
        String customerRiskRating) {
}
