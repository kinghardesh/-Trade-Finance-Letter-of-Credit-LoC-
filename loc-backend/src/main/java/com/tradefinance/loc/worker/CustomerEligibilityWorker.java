package com.tradefinance.loc.worker;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradefinance.loc.mock.MockCustomerRegistry;
import com.tradefinance.loc.mock.MockCustomerRegistry.EligibilityResult;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN: "Check customer eligibility" (Task_CheckEligibility), job type "check-customer-eligibility".
 * Returns customerEligible + customerRiskRating (+ rejectionReason when not eligible).
 */
@Component
public class CustomerEligibilityWorker {

    private static final Logger log = LoggerFactory.getLogger(CustomerEligibilityWorker.class);

    private final MockCustomerRegistry customerRegistry;

    public CustomerEligibilityWorker(MockCustomerRegistry customerRegistry) {
        this.customerRegistry = customerRegistry;
    }

    @JobWorker(type = "check-customer-eligibility")
    public Map<String, Object> checkEligibility(@Variable String applicationId,
                                                @Variable String customerId,
                                                @Variable BigDecimal amount) {
        EligibilityResult result = customerRegistry.check(customerId, amount);
        log.info("[check-customer-eligibility] {} customer={} -> {}", applicationId, customerId, result);

        Map<String, Object> out = new HashMap<>();
        out.put("customerEligible", result.eligible());
        out.put("customerRiskRating", result.riskRating());
        if (!result.eligible()) {
            out.put("rejectionReason", result.reason());
        }
        return out;
    }
}
