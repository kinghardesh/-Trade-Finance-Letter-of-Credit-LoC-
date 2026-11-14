package com.tradefinance.loc.mock;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/**
 * MOCK core-banking customer registry (no real bank system).
 * Demo rules:
 *   - customerId starting with "CUST-9" → KYC expired → not eligible
 *   - risk rating by amount: < 1,000,000 LOW, ≤ 5,000,000 MEDIUM, above HIGH
 */
@Component
public class MockCustomerRegistry {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal FIVE_MILLION = new BigDecimal("5000000");

    public record EligibilityResult(boolean eligible, String riskRating, String reason) {
    }

    public EligibilityResult check(String customerId, BigDecimal amount) {
        String rating = amount.compareTo(ONE_MILLION) < 0 ? "LOW"
                : amount.compareTo(FIVE_MILLION) <= 0 ? "MEDIUM" : "HIGH";

        if (customerId != null && customerId.startsWith("CUST-9")) {
            return new EligibilityResult(false, "HIGH", "Customer KYC review has expired");
        }
        return new EligibilityResult(true, rating, null);
    }
}
