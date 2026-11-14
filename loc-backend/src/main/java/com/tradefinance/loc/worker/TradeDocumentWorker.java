package com.tradefinance.loc.worker;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN: "Verify trade document" (Task_VerifyDocument inside the embedded subprocess),
 * job type "verify-trade-document". SEQUENTIAL MULTI-INSTANCE: one job per element of
 * "tradeDocuments", one after the other; each job sees its element as variable "document".
 *
 * MOCK rule: a document is a discrepancy when its reference contains "DISCREPANCY"
 * (e.g. "BL-DISCREPANCY-1"). Any invalid document makes the subprocess throw the
 * BPMN error DOCUMENT_DISCREPANCY, which sends the application back for correction.
 */
@Component
public class TradeDocumentWorker {

    private static final Logger log = LoggerFactory.getLogger(TradeDocumentWorker.class);

    @JobWorker(type = "verify-trade-document")
    public Map<String, Object> verify(@Variable String applicationId,
                                      @Variable Map<String, Object> document,
                                      @Variable Integer loopCounter) {
        String type = (String) document.get("documentType");
        String reference = (String) document.get("documentReference");

        boolean discrepancy = reference == null || reference.isBlank()
                || reference.toUpperCase(Locale.ROOT).contains("DISCREPANCY");
        String remarks = discrepancy
                ? "Discrepancy: document data does not match the LoC terms"
                : "Document complies with the LoC terms";

        log.info("[verify-trade-document] {} #{} {} {} -> valid={}", applicationId, loopCounter, type, reference, !discrepancy);

        Map<String, Object> documentResult = new HashMap<>();
        documentResult.put("documentType", type);
        documentResult.put("documentReference", reference);
        documentResult.put("valid", !discrepancy);
        documentResult.put("remarks", remarks);
        return Map.of("documentResult", documentResult);
    }
}
