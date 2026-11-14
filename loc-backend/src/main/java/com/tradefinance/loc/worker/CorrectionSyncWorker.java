package com.tradefinance.loc.worker;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradefinance.loc.service.ApplicationService;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;

/**
 * END execution listener of "Correct application" (Task_CorrectApplication), job type "sync-corrections".
 * Runs after the user submitted the correction — in the React app OR in Camunda Tasklist —
 * and BEFORE the token moves on to validation, so PostgreSQL always holds the corrected data.
 */
@Component
public class CorrectionSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(CorrectionSyncWorker.class);

    private final ApplicationService applicationService;

    public CorrectionSyncWorker(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @JobWorker(type = "sync-corrections", fetchVariables = {
            "applicationId", "applicantName", "applicantEmail", "applicantCountry",
            "beneficiaryName", "beneficiaryCountry", "beneficiaryBankName", "amount", "currency",
            "goodsDescription", "latestShipmentDate", "expiryDate", "tradeDocuments"})
    public void syncCorrections(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String applicationId = (String) variables.get("applicationId");
        applicationService.applyCorrections(applicationId, variables);
        log.info("[sync-corrections] {} stored corrected application data", applicationId);
    }
}
