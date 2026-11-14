package com.tradefinance.loc.worker;

import java.time.LocalDate;
import java.time.Year;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradefinance.loc.service.ApplicationService;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN: "Issue Letter of Credit" (Task_IssueLoC), job type "issue-loc".
 * Assigns the LoC number, stores status ISSUED. The next task (Email connector)
 * uses locNumber and issueDate in the customer email.
 */
@Component
public class IssueLocWorker {

    private static final Logger log = LoggerFactory.getLogger(IssueLocWorker.class);

    // Step 7 replaces this counter with a PostgreSQL sequence
    private final AtomicInteger sequence = new AtomicInteger();
    private final ApplicationService applicationService;

    public IssueLocWorker(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @JobWorker(type = "issue-loc")
    public Map<String, Object> issue(@Variable String applicationId) {
        String locNumber = "LC-%d-%06d".formatted(Year.now().getValue(), sequence.incrementAndGet());
        String issueDate = LocalDate.now().toString();

        applicationService.markIssued(applicationId, locNumber);
        log.info("[issue-loc] {} -> {} issued on {}", applicationId, locNumber, issueDate);

        return Map.of(
                "locNumber", locNumber,
                "issueDate", issueDate);
    }
}
