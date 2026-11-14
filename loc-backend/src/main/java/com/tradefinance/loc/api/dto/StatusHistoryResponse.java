package com.tradefinance.loc.api.dto;

import java.time.OffsetDateTime;

import com.tradefinance.loc.domain.StatusHistoryEntry;

/** One entry of GET /api/applications/{id}/history (status timeline). */
public record StatusHistoryResponse(String status, OffsetDateTime changedAt) {

    public static StatusHistoryResponse from(StatusHistoryEntry e) {
        return new StatusHistoryResponse(e.getStatus().name(), e.getChangedAt());
    }
}
