package com.tradefinance.loc.worker;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradefinance.loc.mock.MockAmlEngine;
import com.tradefinance.loc.mock.MockAmlEngine.AmlResult;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN (compliance process): "Perform AML review" (Task_AmlReview), job type "aml-review".
 * Returns amlRiskScore + amlFindings. The task's FEEL output mapping turns the score into
 * complianceStatus ("CLEAR" if < 70, else "REJECTED").
 */
@Component
public class AmlReviewWorker {

    private static final Logger log = LoggerFactory.getLogger(AmlReviewWorker.class);

    private final MockAmlEngine amlEngine;

    public AmlReviewWorker(MockAmlEngine amlEngine) {
        this.amlEngine = amlEngine;
    }

    @JobWorker(type = "aml-review")
    public Map<String, Object> review(@Variable String applicationId,
                                      @Variable BigDecimal amount,
                                      @Variable String customerRiskRating,
                                      @Variable String goodsDescription) {
        AmlResult result = amlEngine.review(amount, customerRiskRating, goodsDescription);
        log.info("[aml-review] {} -> score={} {}", applicationId, result.riskScore(), result.findings());
        return Map.of(
                "amlRiskScore", result.riskScore(),
                "amlFindings", result.findings());
    }
}
