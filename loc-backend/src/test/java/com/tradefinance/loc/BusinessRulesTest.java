package com.tradefinance.loc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tradefinance.loc.mock.MockAmlEngine;
import com.tradefinance.loc.mock.MockCustomerRegistry;
import com.tradefinance.loc.mock.MockSanctionsList;
import com.tradefinance.loc.service.ApplicationValidator;

/** Plain unit tests (no Spring, no Camunda) for the rules used by the job workers. */
class BusinessRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final List<Map<String, Object>> DOCS = List.of(
            Map.of("documentType", "COMMERCIAL_INVOICE"),
            Map.of("documentType", "BILL_OF_LADING"));

    private final ApplicationValidator validator = new ApplicationValidator();

    @Test
    void validApplicationHasNoErrors() {
        assertThat(validator.validate("2026-12-15", "2027-01-31", "DE", "CN",
                new BigDecimal("250000"), DOCS, TODAY)).isEmpty();
    }

    @Test
    void invalidApplicationListsEveryProblem() {
        List<String> errors = validator.validate("2026-12-15", "2026-12-20", "DE", "DE",
                new BigDecimal("60000000"), List.of(Map.of("documentType", "PACKING_LIST")), TODAY);
        assertThat(errors).hasSize(5)
                .anyMatch(e -> e.contains("21 days"))
                .anyMatch(e -> e.contains("different countries"))
                .anyMatch(e -> e.contains("maximum"))
                .anyMatch(e -> e.contains("COMMERCIAL_INVOICE"))
                .anyMatch(e -> e.contains("BILL_OF_LADING"));
    }

    @Test
    void customerRegistryRules() {
        var registry = new MockCustomerRegistry();
        assertThat(registry.check("CUST-1001", new BigDecimal("250000")).riskRating()).isEqualTo("LOW");
        assertThat(registry.check("CUST-1001", new BigDecimal("2000000")).riskRating()).isEqualTo("MEDIUM");
        assertThat(registry.check("CUST-1001", new BigDecimal("6000000")).riskRating()).isEqualTo("HIGH");
        assertThat(registry.check("CUST-9001", new BigDecimal("1000")).eligible()).isFalse();
    }

    @Test
    void sanctionsListRules() {
        var list = new MockSanctionsList();
        assertThat(list.screen("Rhein Machinery GmbH", "DE").matchFound()).isFalse();
        assertThat(list.screen("Blocked Trading Co", "CN").matchFound()).isTrue();
        assertThat(list.screen("Any Name", "KP").matchFound()).isTrue();
    }

    @Test
    void amlRules() {
        var aml = new MockAmlEngine();
        assertThat(aml.review(new BigDecimal("250000"), "LOW", "CNC machines").riskScore()).isEqualTo(10);
        // 6M + HIGH rating = 10+20+15+15 = 60 → still below 70, so credit check can decline it later
        assertThat(aml.review(new BigDecimal("6000000"), "HIGH", "CNC machines").riskScore()).isEqualTo(60);
        assertThat(aml.review(new BigDecimal("250000"), "LOW", "military vehicles").riskScore()).isGreaterThanOrEqualTo(70);
    }
}
