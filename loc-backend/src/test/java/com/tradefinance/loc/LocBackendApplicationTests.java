package com.tradefinance.loc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tradefinance.loc.api.dto.CreditAssessmentRequest;
import com.tradefinance.loc.domain.ApplicationStatus;
import com.tradefinance.loc.domain.LocApplication;
import com.tradefinance.loc.domain.TradeDocument;
import com.tradefinance.loc.repository.ApplicationRepository;
import com.tradefinance.loc.service.ApplicationService;
import com.tradefinance.loc.service.CreditBureauMockService;

/**
 * Starts the whole Spring context against an in-memory H2 database (profile "test"):
 * Flyway runs V1__initial_schema.sql and Hibernate validates every entity against it.
 * No Camunda needed.
 */
@SpringBootTest
@ActiveProfiles("test")
class LocBackendApplicationTests {

    @Autowired
    CreditBureauMockService creditBureau;

    @Autowired
    ApplicationRepository repository;

    @Autowired
    ApplicationService applicationService;

    @Test
    void contextLoadsAndSchemaMatchesEntities() {
    }

    @Test
    void storesApplicationWithDocumentsStatusHistoryAndSequences() {
        LocApplication app = new LocApplication();
        app.setApplicationId("LOC-APP-TEST-" + repository.nextApplicationNumber());
        app.setCustomerId("CUST-1001");
        app.setApplicantName("Rhein Machinery GmbH");
        app.setApplicantEmail("buyer@example.com");
        app.setApplicantCountry("DE");
        app.setBeneficiaryName("Shanghai Precision Tools Ltd");
        app.setBeneficiaryCountry("CN");
        app.setBeneficiaryBankName("Bank of Example");
        app.setAmount(new BigDecimal("250000.00"));
        app.setCurrency("EUR");
        app.setGoodsDescription("CNC machines");
        app.setLatestShipmentDate(LocalDate.of(2026, 12, 15));
        app.setExpiryDate(LocalDate.of(2027, 1, 31));
        app.setTradeDocuments(List.of(
                new TradeDocument("COMMERCIAL_INVOICE", "INV-1", "invoice.pdf"),
                new TradeDocument("BILL_OF_LADING", "BL-1", null)));
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(OffsetDateTime.now());
        app.setUpdatedAt(OffsetDateTime.now());
        repository.save(app);

        applicationService.updateStatus(app.getApplicationId(), ApplicationStatus.VALIDATING);
        applicationService.updateStatus(app.getApplicationId(), ApplicationStatus.VALIDATING); // no duplicate
        applicationService.setCorrectionReasons(app.getApplicationId(), List.of("reason A", "reason B"));
        applicationService.markIssued(app.getApplicationId(), applicationService.nextLocNumber());

        LocApplication loaded = applicationService.findById(app.getApplicationId());
        assertThat(loaded.getStatus()).isEqualTo(ApplicationStatus.ISSUED);
        assertThat(loaded.getLocNumber()).matches("LC-\\d{4}-\\d{6}");
        assertThat(loaded.getTradeDocuments()).extracting(TradeDocument::documentType)
                .containsExactly("COMMERCIAL_INVOICE", "BILL_OF_LADING");
        assertThat(loaded.getCorrectionReasons()).containsExactly("reason A", "reason B");
        assertThat(applicationService.findHistory(app.getApplicationId()))
                .extracting(h -> h.getStatus().name())
                .containsExactly("VALIDATING", "ISSUED");
    }

    @Test
    void creditBureauMockApprovesLowRiskAndDeclinesHugeAmounts() {
        var low = creditBureau.assess(new CreditAssessmentRequest("A", "C", new BigDecimal("250000"), "EUR", "LOW"));
        assertThat(low.creditApproved()).isTrue();
        assertThat(low.riskLevel()).isEqualTo("LOW");

        var risky = creditBureau.assess(new CreditAssessmentRequest("A", "C", new BigDecimal("6000000"), "EUR", "MEDIUM"));
        assertThat(risky.creditApproved()).isFalse(); // 690 - 150 = 540 → HIGH, declined
        assertThat(risky.riskLevel()).isEqualTo("HIGH");
    }
}
