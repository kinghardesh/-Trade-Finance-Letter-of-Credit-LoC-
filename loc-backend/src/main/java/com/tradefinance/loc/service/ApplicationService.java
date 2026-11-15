package com.tradefinance.loc.service;

import java.time.OffsetDateTime;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradefinance.loc.api.dto.CreateApplicationRequest;
import com.tradefinance.loc.domain.ApplicationStatus;
import com.tradefinance.loc.domain.LocApplication;
import com.tradefinance.loc.domain.StatusHistoryEntry;
import com.tradefinance.loc.domain.TradeDocument;
import com.tradefinance.loc.exception.NotFoundException;
import com.tradefinance.loc.exception.WorkflowException;
import com.tradefinance.loc.repository.ApplicationRepository;
import com.tradefinance.loc.repository.StatusHistoryRepository;
import com.tradefinance.loc.workflow.ProcessConstants;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Variable;

/**
 * Saves LoC applications in PostgreSQL and starts the "loc-issuance" BPMN process for each one.
 * Also offers the small update methods the job workers call to keep the database in sync.
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository repository;
    private final StatusHistoryRepository historyRepository;
    private final CamundaClient camundaClient;
    private final ObjectMapper objectMapper;
    private final String creditBureauUrl;

    public ApplicationService(ApplicationRepository repository, StatusHistoryRepository historyRepository,
                              CamundaClient camundaClient, ObjectMapper objectMapper,
                              @Value("${loc.credit-bureau.url}") String creditBureauUrl) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.camundaClient = camundaClient;
        this.objectMapper = objectMapper;
        this.creditBureauUrl = creditBureauUrl;
    }

    // ------------------------------------------------------------------
    // Submit = save in DB, then start the workflow
    // ------------------------------------------------------------------

    /**
     * Deliberately NOT one big transaction: the row must be committed BEFORE the process starts,
     * because the first job workers run within milliseconds and look the application up.
     */
    public LocApplication submit(CreateApplicationRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        LocApplication app = toEntity(request);
        app.setApplicationId("LOC-APP-%d-%04d".formatted(Year.now().getValue(), repository.nextApplicationNumber()));
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        repository.save(app);
        historyRepository.save(new StatusHistoryEntry(app.getApplicationId(), ApplicationStatus.SUBMITTED, now));

        Map<String, Object> variables = toProcessVariables(app);
        if (request.correctionTimeout() != null) {
            variables.put("correctionTimeout", request.correctionTimeout());
        }
        if (request.approvalTimeout() != null) {
            variables.put("approvalTimeout", request.approvalTimeout());
        }
        // where the BPMN REST connector finds the (mock) credit bureau
        variables.put("creditBureauUrl", creditBureauUrl);

        ProcessInstanceEvent instance;
        try {
            instance = camundaClient.newCreateInstanceCommand()
                    .bpmnProcessId(ProcessConstants.MAIN_PROCESS_ID)
                    .latestVersion()
                    .variables(variables)
                    .send()
                    .join();
        } catch (RuntimeException e) {
            // Do not keep an application (or its history) whose workflow never started
            historyRepository.deleteByApplicationId(app.getApplicationId());
            repository.deleteById(app.getApplicationId());
            throw new WorkflowException("Could not start the LoC workflow in Camunda: " + e.getMessage(), e);
        }

        setProcessInstanceKey(app.getApplicationId(), instance.getProcessInstanceKey());
        log.info("Started process {} (instance {}) for application {}",
                instance.getBpmnProcessId(), instance.getProcessInstanceKey(), app.getApplicationId());
        return findById(app.getApplicationId());
    }

    @Transactional
    public void setProcessInstanceKey(String applicationId, long processInstanceKey) {
        repository.setProcessInstanceKey(applicationId, processInstanceKey, OffsetDateTime.now());
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LocApplication> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public LocApplication findById(String applicationId) {
        return repository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application " + applicationId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryEntry> findHistory(String applicationId) {
        findById(applicationId); // 404 if unknown
        return historyRepository.findByApplicationIdOrderByChangedAtAscIdAsc(applicationId);
    }

    // ------------------------------------------------------------------
    // Called by job workers / execution listeners to keep the DB in sync
    // ------------------------------------------------------------------

    /** Sets the status and writes a history row; ignores ids unknown to this backend. */
    @Transactional
    public void updateStatus(String applicationId, ApplicationStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        if (repository.updateStatus(applicationId, status, now) == 1) {
            historyRepository.save(new StatusHistoryEntry(applicationId, status, now));
            log.info("Application {} -> {}", applicationId, status);
        }
    }

    /** Why the application needs correction (shown on the "Correct application" screen). */
    @Transactional
    public void setCorrectionReasons(String applicationId, List<String> reasons) {
        repository.findById(applicationId).ifPresent(app -> app.setCorrectionReasons(reasons));
    }

    @Transactional
    public void markIssued(String applicationId, String locNumber) {
        repository.setLocNumber(applicationId, locNumber, OffsetDateTime.now());
        updateStatus(applicationId, ApplicationStatus.ISSUED);
    }

    @Transactional
    public void markRejected(String applicationId, String reason) {
        repository.setRejectionReason(applicationId, reason, OffsetDateTime.now());
        updateStatus(applicationId, ApplicationStatus.REJECTED);
    }

    /** Next LoC number from the database sequence, e.g. LC-2026-000001. */
    @Transactional
    public String nextLocNumber() {
        return "LC-%d-%06d".formatted(Year.now().getValue(), repository.nextLocNumber());
    }

    /**
     * Copies corrected fields from the "Correct application" task into the stored application.
     * Called by the end listener of that task, so it works whether the task was completed
     * in the React app (our API) or in Camunda Tasklist.
     */
    @Transactional
    public void applyCorrections(String applicationId, Map<String, Object> corrections) {
        repository.findById(applicationId).ifPresent(app -> {
            Map<String, Object> merged = new LinkedHashMap<>(toProcessVariables(app));
            merged.putAll(corrections);
            LocApplication corrected = objectMapper.convertValue(merged, LocApplication.class);
            app.setApplicantName(corrected.getApplicantName());
            app.setApplicantEmail(corrected.getApplicantEmail());
            app.setApplicantCountry(corrected.getApplicantCountry());
            app.setBeneficiaryName(corrected.getBeneficiaryName());
            app.setBeneficiaryCountry(corrected.getBeneficiaryCountry());
            app.setBeneficiaryBankName(corrected.getBeneficiaryBankName());
            app.setAmount(corrected.getAmount());
            app.setCurrency(corrected.getCurrency());
            app.setGoodsDescription(corrected.getGoodsDescription());
            app.setLatestShipmentDate(corrected.getLatestShipmentDate());
            app.setExpiryDate(corrected.getExpiryDate());
            app.setTradeDocuments(corrected.getTradeDocuments());
            app.setUpdatedAt(OffsetDateTime.now());
            // no save() needed: changes to a loaded entity are written when the transaction commits
        });
    }

    /**
     * Current process-level variables of the application's workflow (read from Camunda).
     * Camunda's search data is updated a moment after the engine, so values can lag ~1 second.
     */
    public Map<String, Object> findProcessVariables(LocApplication app) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (app.getProcessInstanceKey() == null) {
            return result;
        }
        try {
            List<Variable> variables = camundaClient.newVariableSearchRequest()
                    .filter(f -> f.processInstanceKey(app.getProcessInstanceKey()))
                    .page(p -> p.limit(200))
                    .send()
                    .join()
                    .items();
            for (Variable v : variables) {
                // keep only the top (process) scope, not variables local to subprocesses
                if (app.getProcessInstanceKey().equals(v.getScopeKey())) {
                    result.put(v.getName(), parseJson(v.getValue()));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not read process variables for {}: {}", app.getApplicationId(), e.getMessage());
        }
        return result;
    }

    /** Converts the application into the BPMN input variables (docs/process-variables.md section 1). */
    private Map<String, Object> toProcessVariables(LocApplication app) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("applicationId", app.getApplicationId());
        v.put("customerId", app.getCustomerId());
        v.put("applicantName", app.getApplicantName());
        v.put("applicantEmail", app.getApplicantEmail());
        v.put("applicantCountry", app.getApplicantCountry());
        v.put("beneficiaryName", app.getBeneficiaryName());
        v.put("beneficiaryCountry", app.getBeneficiaryCountry());
        v.put("beneficiaryBankName", app.getBeneficiaryBankName());
        v.put("amount", app.getAmount());
        v.put("currency", app.getCurrency());
        v.put("goodsDescription", app.getGoodsDescription());
        // dates as ISO strings ("2026-12-15") so FEEL and the workers read them easily
        v.put("latestShipmentDate", app.getLatestShipmentDate().toString());
        v.put("expiryDate", app.getExpiryDate().toString());
        v.put("tradeDocuments", app.getTradeDocuments().stream()
                .map(d -> {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("documentType", d.documentType());
                    doc.put("documentReference", d.documentReference());
                    doc.put("fileName", d.fileName());
                    return doc;
                })
                .toList());
        return v;
    }

    private LocApplication toEntity(CreateApplicationRequest r) {
        LocApplication a = new LocApplication();
        a.setCustomerId(r.customerId());
        a.setApplicantName(r.applicantName());
        a.setApplicantEmail(r.applicantEmail());
        a.setApplicantCountry(r.applicantCountry());
        a.setBeneficiaryName(r.beneficiaryName());
        a.setBeneficiaryCountry(r.beneficiaryCountry());
        a.setBeneficiaryBankName(r.beneficiaryBankName());
        a.setAmount(r.amount());
        a.setCurrency(r.currency());
        a.setGoodsDescription(r.goodsDescription());
        a.setLatestShipmentDate(r.latestShipmentDate());
        a.setExpiryDate(r.expiryDate());
        a.setTradeDocuments(r.tradeDocuments().stream()
                .map(d -> new TradeDocument(d.documentType(), d.documentReference(), d.fileName()))
                .toList());
        return a;
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
