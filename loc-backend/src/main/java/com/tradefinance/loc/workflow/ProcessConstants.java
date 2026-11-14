package com.tradefinance.loc.workflow;

import java.util.Set;

/**
 * Names that must match the BPMN files exactly (see docs/process-variables.md).
 * Keeping them in one place avoids typos between BPMN, Java and the React app.
 */
public final class ProcessConstants {

    private ProcessConstants() {
    }

    // Process ids
    public static final String MAIN_PROCESS_ID = "loc-issuance";
    public static final String COMPLIANCE_PROCESS_ID = "loc-compliance-screening";

    // User task element ids
    public static final String TASK_CORRECT_APPLICATION = "Task_CorrectApplication";
    public static final String TASK_MANAGER_APPROVAL = "Task_ManagerApproval";
    public static final String TASK_ESCALATED_APPROVAL = "Task_EscalatedApproval";
    public static final String TASK_VERIFY_TERMS = "Task_VerifyTerms";

    // Variables submitted by user tasks
    public static final String VAR_APPROVAL_DECISION = "approvalDecision";
    public static final String VAR_APPROVER_COMMENTS = "approverComments";
    public static final String VAR_TERMS_VERIFIED = "termsVerified";
    public static final String VAR_TERMS_COMMENTS = "termsComments";

    public static final Set<String> APPROVAL_DECISIONS = Set.of("APPROVED", "REJECTED");
}
