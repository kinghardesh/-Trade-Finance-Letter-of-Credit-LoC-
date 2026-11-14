package com.tradefinance.loc.worker;

import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradefinance.loc.service.ApplicationService;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN: "Issue Letter of Credit" (Task_IssueLoC), job type "issue-loc".
 * Takes the next LoC number from the PostgreSQL sequence loc_number_seq, stores
 * status ISSUED. The next task (Email connector) uses locNumber and issueDate.
 */
@Component
public class IssueLocWorker {

    private static final Logger log = LoggerFactory.getLogger(IssueLocWorker.class);

    private final ApplicationService applicationService;

    public IssueLocWorker(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @JobWorker(type = "issue-loc")
    public Map<String, Object> issue(@Variable String applicationId) {
        String number = applicationService.nextLocNumber();
        String issueDate = LocalDate.now().toString();

        applicationService.markIssued(applicationId, number);
        log.info("[issue-loc] {} -> {} issued on {}", applicationId, number, issueDate);

        return Map.of(
                "locNumber", number,
                "issueDate", issueDate);
    }
}
