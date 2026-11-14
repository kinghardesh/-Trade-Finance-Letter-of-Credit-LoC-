package com.tradefinance.loc.worker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradefinance.loc.service.ApplicationValidator;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN: "Validate application details" (Task_ValidateApplication), job type "validate-application".
 * Returns applicationValid + validationErrors → gateway "Application valid?".
 */
@Component
public class ValidateApplicationWorker {

    private static final Logger log = LoggerFactory.getLogger(ValidateApplicationWorker.class);

    private final ApplicationValidator validator;

    public ValidateApplicationWorker(ApplicationValidator validator) {
        this.validator = validator;
    }

    @JobWorker(type = "validate-application")
    public Map<String, Object> validate(@Variable String applicationId,
                                        @Variable String latestShipmentDate,
                                        @Variable String expiryDate,
                                        @Variable String applicantCountry,
                                        @Variable String beneficiaryCountry,
                                        @Variable BigDecimal amount,
                                        @Variable List<Map<String, Object>> tradeDocuments) {
        List<String> errors = validator.validate(latestShipmentDate, expiryDate, applicantCountry,
                beneficiaryCountry, amount, tradeDocuments, LocalDate.now());

        log.info("[validate-application] {} -> valid={} {}", applicationId, errors.isEmpty(), errors);
        // The returned map becomes process variables when the job is completed automatically
        return Map.of(
                "applicationValid", errors.isEmpty(),
                "validationErrors", errors);
    }
}
