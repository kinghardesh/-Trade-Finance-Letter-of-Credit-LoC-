package com.tradefinance.loc.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradefinance.loc.domain.StatusHistoryEntry;

/** Table application_status_history. */
public interface StatusHistoryRepository extends JpaRepository<StatusHistoryEntry, Long> {

    List<StatusHistoryEntry> findByApplicationIdOrderByChangedAtAscIdAsc(String applicationId);
}
