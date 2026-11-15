package com.tradefinance.loc.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.tradefinance.loc.domain.StatusHistoryEntry;

/** Table application_status_history. */
public interface StatusHistoryRepository extends JpaRepository<StatusHistoryEntry, Long> {

    List<StatusHistoryEntry> findByApplicationIdOrderByChangedAtAscIdAsc(String applicationId);

    /** Used when the workflow could not be started: remove the history of the deleted application. */
    @Transactional
    void deleteByApplicationId(String applicationId);
}
