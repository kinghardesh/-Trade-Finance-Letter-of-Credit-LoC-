package com.tradefinance.loc.repository;

import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

import com.tradefinance.loc.domain.LocApplication;

/**
 * TEMPORARY in-memory storage (Step 5). Data is lost when the backend restarts.
 * Step 7 replaces this class with a Spring Data JPA repository backed by PostgreSQL,
 * keeping the same method names so the services do not change.
 */
@Repository
public class ApplicationRepository {

    private final Map<String, LocApplication> store = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    /** Generates ids like LOC-APP-2026-0001. */
    public String nextApplicationId() {
        return "LOC-APP-%d-%04d".formatted(Year.now().getValue(), sequence.incrementAndGet());
    }

    public LocApplication save(LocApplication application) {
        store.put(application.getApplicationId(), application);
        return application;
    }

    public Optional<LocApplication> findById(String applicationId) {
        return Optional.ofNullable(store.get(applicationId));
    }

    public Optional<LocApplication> findByProcessInstanceKey(Long processInstanceKey) {
        return store.values().stream()
                .filter(a -> processInstanceKey.equals(a.getProcessInstanceKey()))
                .findFirst();
    }

    public List<LocApplication> findAllNewestFirst() {
        return store.values().stream()
                .sorted(Comparator.comparing(LocApplication::getCreatedAt).reversed())
                .toList();
    }

    public void deleteById(String applicationId) {
        store.remove(applicationId);
    }
}
