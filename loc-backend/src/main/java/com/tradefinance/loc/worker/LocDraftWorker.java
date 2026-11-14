package com.tradefinance.loc.worker;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN: "Generate LoC draft" (Task_GenerateDraft), job type "generate-loc-draft".
 * Runs again (new version) each time "Verify LoC terms" answers termsVerified = false.
 */
@Component
public class LocDraftWorker {

    private static final Logger log = LoggerFactory.getLogger(LocDraftWorker.class);

    @JobWorker(type = "generate-loc-draft")
    public Map<String, Object> generateDraft(@Variable String applicationId,
                                             @Variable String applicantName,
                                             @Variable String beneficiaryName,
                                             @Variable String beneficiaryBankName,
                                             @Variable BigDecimal amount,
                                             @Variable String currency,
                                             @Variable String goodsDescription,
                                             @Variable String latestShipmentDate,
                                             @Variable String expiryDate,
                                             @Variable(optional = true) Integer draftVersion) {
        int version = draftVersion == null ? 1 : draftVersion + 1;
        String reference = "DRAFT-%s-v%d".formatted(applicationId, version);

        String draftText = """
                IRREVOCABLE DOCUMENTARY CREDIT (DRAFT %s)
                Applicant: %s
                Beneficiary: %s, advised through %s
                Amount: %s %s
                Goods: %s
                Latest shipment: %s   Expiry: %s
                Subject to UCP 600. Educational demo document - not a real banking instrument.
                """.formatted(reference, applicantName, beneficiaryName, beneficiaryBankName,
                amount.toPlainString(), currency, goodsDescription, latestShipmentDate, expiryDate);

        log.info("[generate-loc-draft] {} -> {}", applicationId, reference);
        return Map.of(
                "draftReference", reference,
                "draftVersion", version,
                "draftText", draftText);
    }
}
