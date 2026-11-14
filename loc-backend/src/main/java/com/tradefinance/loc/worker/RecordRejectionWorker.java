package com.tradefinance.loc.worker;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradefinance.loc.service.ApplicationService;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN: "Record rejection" (Task_RecordRejection), job type "record-rejection".
 * All rejection paths meet here. Makes sure rejectionReason is filled, then stores
 * status REJECTED. The next task (Email connector) puts the reason in the email.
 */
@Component
public class RecordRejectionWorker {

    private static final Logger log = LoggerFactory.getLogger(RecordRejectionWorker.class);

    private final ApplicationService applicationService;

    public RecordRejectionWorker(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @JobWorker(type = "record-rejection")
    public Map<String, Object> recordRejection(@Variable String applicationId,
                                               @Variable(optional = true) String rejectionReason,
                                               @Variable(optional = true) String approvalDecision,
                                               @Variable(optional = true) String approverComments) {
        String reason = rejectionReason;
        if (reason == null || reason.isBlank()) {
            if ("REJECTED".equals(approvalDecision)) {
                reason = "Rejected by credit management"
                        + (approverComments == null || approverComments.isBlank() ? "" : ": " + approverComments);
            } else {
                reason = "Application rejected";
            }
        }

        applicationService.markRejected(applicationId, reason);
        log.info("[record-rejection] {} -> {}", applicationId, reason);
        return Map.of("rejectionReason", reason);
    }
}
