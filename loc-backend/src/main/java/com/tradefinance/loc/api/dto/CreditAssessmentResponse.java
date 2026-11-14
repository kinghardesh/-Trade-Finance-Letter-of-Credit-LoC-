package com.tradefinance.loc.api.dto;

/** Read by the REST connector's result expression (response.body.creditScore, ...). */
public record CreditAssessmentResponse(
        int creditScore,
        String riskLevel,
        boolean creditApproved) {
}
