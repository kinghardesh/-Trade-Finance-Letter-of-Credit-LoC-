package com.tradefinance.loc.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.tradefinance.loc.domain.ApplicationStatus;
import com.tradefinance.loc.domain.LocApplication;

/**
 * Spring Data JPA repository for table loc_application.
 * Spring generates the implementation; method names become SQL queries.
 *
 * The "update ..." methods change ONE thing in a single SQL statement. Job workers
 * run in parallel with REST calls, so targeted updates avoid one overwriting the other.
 */
public interface ApplicationRepository extends JpaRepository<LocApplication, String> {

    Optional<LocApplication> findByProcessInstanceKey(Long processInstanceKey);

    List<LocApplication> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('loc_application_seq')", nativeQuery = true)
    long nextApplicationNumber();

    @Query(value = "select nextval('loc_number_seq')", nativeQuery = true)
    long nextLocNumber();

    @Modifying
    @Transactional
    @Query("update LocApplication a set a.processInstanceKey = :key, a.updatedAt = :now where a.applicationId = :id")
    int setProcessInstanceKey(@Param("id") String applicationId, @Param("key") long processInstanceKey,
                              @Param("now") OffsetDateTime now);

    /** Returns 1 if the status changed, 0 if it already had this status (or the id is unknown). */
    @Modifying
    @Transactional
    @Query("update LocApplication a set a.status = :status, a.updatedAt = :now "
            + "where a.applicationId = :id and a.status <> :status")
    int updateStatus(@Param("id") String applicationId, @Param("status") ApplicationStatus status,
                     @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("update LocApplication a set a.locNumber = :locNumber, a.updatedAt = :now where a.applicationId = :id")
    int setLocNumber(@Param("id") String applicationId, @Param("locNumber") String locNumber,
                     @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("update LocApplication a set a.rejectionReason = :reason, a.updatedAt = :now where a.applicationId = :id")
    int setRejectionReason(@Param("id") String applicationId, @Param("reason") String reason,
                           @Param("now") OffsetDateTime now);
}
