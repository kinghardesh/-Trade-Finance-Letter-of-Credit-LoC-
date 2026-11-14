package com.tradefinance.loc.worker;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tradefinance.loc.domain.ApplicationStatus;
import com.tradefinance.loc.service.ApplicationService;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;

/**
 * EXECUTION LISTENER worker (job type "update-status").
 * The BPMN has a start listener on 8 elements; whenever the token ENTERS one of them,
 * Camunda creates this job. job.getElementId() tells us which step started,
 * so we can set the matching application status.
 */
@Component
public class StatusListenerWorker {

    private static final Map<String, ApplicationStatus> STATUS_BY_ELEMENT = Map.of(
            "Task_ValidateApplication", ApplicationStatus.VALIDATING,
            "Task_CorrectApplication", ApplicationStatus.CORRECTION_REQUIRED,
            "CallActivity_ComplianceScreening", ApplicationStatus.COMPLIANCE_CHECK,
            "SubProcess_VerifyDocuments", ApplicationStatus.DOCUMENT_VERIFICATION,
            "Task_CreditAssessment", ApplicationStatus.CREDIT_ASSESSMENT,
            "Task_ManagerApproval", ApplicationStatus.PENDING_APPROVAL,
            "Task_EscalatedApproval", ApplicationStatus.ESCALATED,
            "Task_GenerateDraft", ApplicationStatus.DRAFT_GENERATED);

    private final ApplicationService applicationService;

    public StatusListenerWorker(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @JobWorker(type = "update-status")
    public void onElementStarted(ActivatedJob job,
                                 @Variable String applicationId,
                                 @Variable(optional = true) List<String> validationErrors,
                                 @Variable(optional = true) String correctionReason) {
        ApplicationStatus status = STATUS_BY_ELEMENT.get(job.getElementId());
        if (status == null || applicationId == null) {
            return;
        }

        if (status == ApplicationStatus.CORRECTION_REQUIRED) {
            // store WHY right now, so the React app does not wait for Camunda's search index
            List<String> reasons = validationErrors != null && !validationErrors.isEmpty()
                    ? validationErrors
                    : correctionReason != null ? List.of(correctionReason) : List.of();
            applicationService.setCorrectionReasons(applicationId, reasons);
        } else if (status == ApplicationStatus.VALIDATING) {
            applicationService.setCorrectionReasons(applicationId, List.of());
        }

        applicationService.updateStatus(applicationId, status);
    }
}
