package com.tradefinance.loc.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradefinance.loc.api.dto.TaskResponse;
import com.tradefinance.loc.domain.LocApplication;
import com.tradefinance.loc.exception.NotFoundException;
import com.tradefinance.loc.exception.WorkflowException;
import com.tradefinance.loc.repository.ApplicationRepository;
import com.tradefinance.loc.workflow.ProcessConstants;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;

/**
 * Lists and completes Camunda user tasks for the React app
 * (Correct application, Manager approval, Escalated approval, Verify LoC terms).
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final CamundaClient camundaClient;
    private final ApplicationRepository repository;
    private final ApplicationService applicationService;

    public TaskService(CamundaClient camundaClient, ApplicationRepository repository,
                       ApplicationService applicationService) {
        this.camundaClient = camundaClient;
        this.repository = repository;
        this.applicationService = applicationService;
    }

    /** Open tasks, optionally filtered by candidate group and/or application. */
    public List<TaskResponse> findOpenTasks(String candidateGroup, String applicationId) {
        Long processInstanceKey = null;
        if (applicationId != null && !applicationId.isBlank()) {
            processInstanceKey = repository.findById(applicationId)
                    .map(LocApplication::getProcessInstanceKey)
                    .orElse(null);
            if (processInstanceKey == null) {
                return List.of();
            }
        }
        final Long piKey = processInstanceKey;

        try {
            List<UserTask> tasks = camundaClient.newUserTaskSearchRequest()
                    .filter(f -> {
                        f.state(UserTaskState.CREATED).bpmnProcessId(ProcessConstants.MAIN_PROCESS_ID);
                        if (candidateGroup != null && !candidateGroup.isBlank()) {
                            f.candidateGroup(candidateGroup);
                        }
                        if (piKey != null) {
                            f.processInstanceKey(piKey);
                        }
                    })
                    .page(p -> p.limit(100))
                    .send()
                    .join()
                    .items();
            return tasks.stream().map(this::toResponse).toList();
        } catch (RuntimeException e) {
            throw new WorkflowException("Could not load tasks from Camunda: " + e.getMessage(), e);
        }
    }

    /** Validates the submitted variables for the task type, then completes the task in Camunda. */
    public void complete(long userTaskKey, Map<String, Object> variables) {
        UserTask task = getOpenTask(userTaskKey);
        validateVariables(task.getElementId(), variables);

        // Corrected fields are copied to the database by the "sync-corrections" end listener
        // of Task_CorrectApplication (see CorrectionSyncWorker), also when Tasklist is used.
        try {
            camundaClient.newCompleteUserTaskCommand(userTaskKey)
                    .variables(variables)
                    .send()
                    .join();
            log.info("Completed user task {} ({}) with {}", userTaskKey, task.getElementId(), variables);
        } catch (ClientHttpException e) {
            // 404/409 from Camunda: the task was completed a moment ago (the search index can still
            // show it as open for 1-2 seconds) → tell the client "conflict", not "server down"
            if (e.code() == 404 || e.code() == 409) {
                throw new IllegalStateException("Task " + userTaskKey + " was already completed");
            }
            throw new WorkflowException("Could not complete task " + userTaskKey + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new WorkflowException("Could not complete task " + userTaskKey + ": " + e.getMessage(), e);
        }
    }

    private UserTask getOpenTask(long userTaskKey) {
        UserTask task;
        try {
            task = camundaClient.newUserTaskGetRequest(userTaskKey).send().join();
        } catch (RuntimeException e) {
            throw new NotFoundException("Task " + userTaskKey + " not found");
        }
        if (task.getState() != UserTaskState.CREATED) {
            throw new IllegalStateException("Task " + userTaskKey + " is not open (state " + task.getState() + ")");
        }
        return task;
    }

    /** Each user task must send the variables its outgoing gateway needs (see BPMN conditions). */
    private void validateVariables(String elementId, Map<String, Object> vars) {
        switch (elementId) {
            case ProcessConstants.TASK_MANAGER_APPROVAL, ProcessConstants.TASK_ESCALATED_APPROVAL -> {
                Object decision = vars.get(ProcessConstants.VAR_APPROVAL_DECISION);
                if (!(decision instanceof String s) || !ProcessConstants.APPROVAL_DECISIONS.contains(s)) {
                    throw new IllegalArgumentException("approvalDecision must be \"APPROVED\" or \"REJECTED\"");
                }
            }
            case ProcessConstants.TASK_VERIFY_TERMS -> {
                if (!(vars.get(ProcessConstants.VAR_TERMS_VERIFIED) instanceof Boolean)) {
                    throw new IllegalArgumentException("termsVerified must be true or false");
                }
            }
            default -> {
                // Task_CorrectApplication: corrected fields are optional
            }
        }
    }

    private TaskResponse toResponse(UserTask t) {
        Optional<LocApplication> app = repository.findByProcessInstanceKey(t.getProcessInstanceKey());
        return new TaskResponse(
                String.valueOf(t.getUserTaskKey()),
                t.getElementId(),
                t.getName(),
                t.getCandidateGroups(),
                t.getCreationDate(),
                String.valueOf(t.getProcessInstanceKey()),
                app.map(LocApplication::getApplicationId).orElse(null),
                app.map(LocApplication::getApplicantName).orElse(null),
                app.map(LocApplication::getAmount).orElse(null),
                app.map(LocApplication::getCurrency).orElse(null));
    }
}
