package com.tradefinance.loc.mock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * MOCK anti-money-laundering scoring. Score 0–100; the BPMN rejects at ≥ 70.
 *   base 10
 *   +20 amount > 1,000,000   (+15 more if > 5,000,000)
 *   +15 customer risk rating HIGH
 *   +60 goods description mentions arms / weapons / ammunition / military
 */
@Component
public class MockAmlEngine {

    private static final List<String> RESTRICTED_GOODS = List.of("ARMS", "WEAPON", "AMMUNITION", "MILITARY");

    public record AmlResult(int riskScore, List<String> findings) {
    }

    public AmlResult review(BigDecimal amount, String customerRiskRating, String goodsDescription) {
        int score = 10;
        List<String> findings = new ArrayList<>();

        if (amount != null && amount.compareTo(new BigDecimal("1000000")) > 0) {
            score += 20;
            findings.add("High-value transaction (> 1,000,000)");
        }
        if (amount != null && amount.compareTo(new BigDecimal("5000000")) > 0) {
            score += 15;
            findings.add("Very high-value transaction (> 5,000,000)");
        }
        if ("HIGH".equals(customerRiskRating)) {
            score += 15;
            findings.add("Customer risk rating HIGH");
        }
        String goods = goodsDescription == null ? "" : goodsDescription.toUpperCase(Locale.ROOT);
        if (RESTRICTED_GOODS.stream().anyMatch(goods::contains)) {
            score += 60;
            findings.add("Goods description indicates restricted / dual-use goods");
        }
        return new AmlResult(Math.min(score, 100), findings);
    }
}
