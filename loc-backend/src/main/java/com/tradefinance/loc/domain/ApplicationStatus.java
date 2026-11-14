package com.tradefinance.loc.domain;

/**
 * Status shown in the React app and stored in the database.
 * Must match section 3 of docs/process-variables.md.
 */
public enum ApplicationStatus {
    SUBMITTED,
    VALIDATING,
    CORRECTION_REQUIRED,
    COMPLIANCE_CHECK,
    DOCUMENT_VERIFICATION,
    CREDIT_ASSESSMENT,
    PENDING_APPROVAL,
    ESCALATED,
    DRAFT_GENERATED,
    ISSUED,
    REJECTED
}
