package com.tradefinance.loc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tradefinance.loc.api.dto.CreditAssessmentRequest;
import com.tradefinance.loc.service.CreditBureauMockService;

/**
 * Starts the Spring context WITHOUT deploying to Camunda or polling for jobs,
 * so "mvnw package" also works when Camunda 8 Run is not running.
 */
@SpringBootTest(properties = {
        "camunda.client.deployment.enabled=false",
        "camunda.client.worker.defaults.enabled=false"
})
class LocBackendApplicationTests {

    @Autowired
    CreditBureauMockService creditBureau;

    @Test
    void contextLoads() {
    }

    @Test
    void creditBureauMockApprovesLowRiskAndDeclinesHugeAmounts() {
        var low = creditBureau.assess(new CreditAssessmentRequest("A", "C", new BigDecimal("250000"), "EUR", "LOW"));
        assertThat(low.creditApproved()).isTrue();
        assertThat(low.riskLevel()).isEqualTo("LOW");

        var huge = creditBureau.assess(new CreditAssessmentRequest("A", "C", new BigDecimal("6000000"), "EUR", "LOW"));
        assertThat(huge.creditApproved()).isTrue(); // 780 - 150 = 630 → MEDIUM, still approved

        var risky = creditBureau.assess(new CreditAssessmentRequest("A", "C", new BigDecimal("6000000"), "EUR", "MEDIUM"));
        assertThat(risky.creditApproved()).isFalse(); // 690 - 150 = 540 → HIGH, declined
        assertThat(risky.riskLevel()).isEqualTo("HIGH");
    }
}
