package com.tradefinance.loc.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * An open user task for the React task inbox.
 * userTaskKey is a STRING (64-bit number) — send it back unchanged when completing the task.
 */
public record TaskResponse(
        String userTaskKey,
        String taskDefinitionId,
        String name,
        List<String> candidateGroups,
        OffsetDateTime createdAt,
        String processInstanceKey,
        String applicationId,
        String applicantName,
        BigDecimal amount,
        String currency) {
}
