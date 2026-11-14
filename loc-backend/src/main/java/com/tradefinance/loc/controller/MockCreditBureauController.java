package com.tradefinance.loc.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradefinance.loc.api.dto.CreditAssessmentRequest;
import com.tradefinance.loc.api.dto.CreditAssessmentResponse;
import com.tradefinance.loc.service.CreditBureauMockService;

/**
 * MOCK external credit bureau, called by the Camunda REST connector in
 * BPMN task "Perform credit assessment" (not by the React app).
 */
@RestController
@RequestMapping("/api/mock/credit-bureau")
public class MockCreditBureauController {

    private final CreditBureauMockService creditBureau;

    public MockCreditBureauController(CreditBureauMockService creditBureau) {
        this.creditBureau = creditBureau;
    }

    @PostMapping("/assessments")
    public CreditAssessmentResponse assess(@RequestBody CreditAssessmentRequest request) {
        return creditBureau.assess(request);
    }
}
