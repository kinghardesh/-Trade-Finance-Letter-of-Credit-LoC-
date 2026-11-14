package com.tradefinance.loc.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.tradefinance.loc.api.dto.CreditAssessmentRequest;
import com.tradefinance.loc.api.dto.CreditAssessmentResponse;

/**
 * MOCK credit bureau (no real external service). Deterministic so demos are repeatable:
 *   base score by customer risk rating: LOW 780, MEDIUM 690, HIGH 590 (unknown 650)
 *   amount over 1,000,000 → −60 points; over 5,000,000 → −150 points
 *   riskLevel: score ≥ 720 LOW, ≥ 620 MEDIUM, else HIGH
 *   creditApproved: score ≥ 620
 * Demo tip: amount 6,000,000 with a MEDIUM (or HIGH) rating → declined.
 */
@Service
public class CreditBureauMockService {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal FIVE_MILLION = new BigDecimal("5000000");

    public CreditAssessmentResponse assess(CreditAssessmentRequest request) {
        int score = switch (request.customerRiskRating() == null ? "" : request.customerRiskRating()) {
            case "LOW" -> 780;
            case "MEDIUM" -> 690;
            case "HIGH" -> 590;
            default -> 650;
        };

        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        if (amount.compareTo(FIVE_MILLION) > 0) {
            score -= 150;
        } else if (amount.compareTo(ONE_MILLION) > 0) {
            score -= 60;
        }

        String riskLevel = score >= 720 ? "LOW" : score >= 620 ? "MEDIUM" : "HIGH";
        return new CreditAssessmentResponse(score, riskLevel, score >= 620);
    }
}
