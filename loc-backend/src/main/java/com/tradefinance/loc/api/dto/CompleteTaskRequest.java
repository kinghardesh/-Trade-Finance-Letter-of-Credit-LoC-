package com.tradefinance.loc.api.dto;

import java.util.Map;

/**
 * Body of POST /api/tasks/{userTaskKey}/complete.
 * Example (manager approval): { "variables": { "approvalDecision": "APPROVED", "approverComments": "OK" } }
 */
public record CompleteTaskRequest(Map<String, Object> variables) {

    public Map<String, Object> variablesOrEmpty() {
        return variables == null ? Map.of() : variables;
    }
}
