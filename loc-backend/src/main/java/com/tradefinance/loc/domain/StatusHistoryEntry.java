package com.tradefinance.loc.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One status change of an application (table application_status_history). */
@Entity
@Table(name = "application_status_history")
public class StatusHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, length = 30)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    protected StatusHistoryEntry() {
        // for JPA
    }

    public StatusHistoryEntry(String applicationId, ApplicationStatus status, OffsetDateTime changedAt) {
        this.applicationId = applicationId;
        this.status = status;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public ApplicationStatus getStatus() { return status; }
    public OffsetDateTime getChangedAt() { return changedAt; }
}
