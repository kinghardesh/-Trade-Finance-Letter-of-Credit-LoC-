package com.tradefinance.loc.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradefinance.loc.api.dto.CreateApplicationRequest;
import com.tradefinance.loc.domain.ApplicationStatus;
import com.tradefinance.loc.domain.LocApplication;
import com.tradefinance.loc.domain.TradeDocument;
import com.tradefinance.loc.exception.NotFoundException;
import com.tradefinance.loc.exception.WorkflowException;
import com.tradefinance.loc.repository.ApplicationRepository;
import com.tradefinance.loc.workflow.ProcessConstants;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Variable;

/**
 * Saves LoC applications and starts the "loc-issuance" BPMN process for each one.
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository repository;
    private final CamundaClient camundaClient;
    private final ObjectMapper objectMapper;
    private final String creditBureauUrl;

    public ApplicationService(ApplicationRepository repository, CamundaClient camundaClient, ObjectMapper objectMapper,
                              @Value("${loc.credit-bureau.url}") String creditBureauUrl) {
        this.repository = repository;
        this.camundaClient = camundaClient;
        this.objectMapper = objectMapper;
        this.creditBureauUrl = creditBureauUrl;
    }

    // ------------------------------------------------------------------
    // Called by job workers / execution listeners to keep the DB in sync
    // ------------------------------------------------------------------

    /** Sets the status; ignores ids unknown to this backend (e.g. instances started from Modeler). */
    public void updateStatus(String applicationId, ApplicationStatus status) {
        repository.findById(applicationId).ifPresentOrElse(app -> {
            if (app.getStatus() != status) {
                log.info("Application {}: {} -> {}", applicationId, app.getStatus(), status);
                app.setStatus(status);
                app.setUpdatedAt(OffsetDateTime.now());
                repository.save(app);
            }
        }, () -> log.debug("Status {} for unknown application {} ignored", status, applicationId));
    }

    /** Why the application needs correction (shown on the "Correct application" screen). */
    public void setCorrectionReasons(String applicationId, List<String> reasons) {
        repository.findById(applicationId).ifPresent(app -> {
            app.setCorrectionReasons(List.copyOf(reasons));
            repository.save(app);
        });
    }

    public void markIssued(String applicationId, String locNumber) {
        repository.findById(applicationId).ifPresent(app -> {
            app.setLocNumber(locNumber);
            repository.save(app);
        });
        updateStatus(applicationId, ApplicationStatus.ISSUED);
    }

    public void markRejected(String applicationId, String reason) {
        repository.findById(applicationId).ifPresent(app -> {
            app.setRejectionReason(reason);
            repository.save(app);
        });
        updateStatus(applicationId, ApplicationStatus.REJECTED);
    }

    /** Copies corrected fields from the "Correct application" task into the stored application. */
    public void applyCorrections(Long processInstanceKey, Map<String, Object> corrections) {
        repository.findByProcessInstanceKey(processInstanceKey).ifPresent(app -> {
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
            repository.save(app);
        });
    }

    /** 1) store the application, 2) start a process instance with its data as variables. */
    public LocApplication submit(CreateApplicationRequest request) {
        LocApplication app = toEntity(request);
        app.setApplicationId(repository.nextApplicationId());
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(OffsetDateTime.now());
        app.setUpdatedAt(app.getCreatedAt());
        repository.save(app);

        Map<String, Object> variables = toProcessVariables(app);
        if (request.correctionTimeout() != null) {
            variables.put("correctionTimeout", request.correctionTimeout());
        }
        if (request.approvalTimeout() != null) {
            variables.put("approvalTimeout", request.approvalTimeout());
        }
        // where the BPMN REST connector finds the (mock) credit bureau
        variables.put("creditBureauUrl", creditBureauUrl);

        try {
            ProcessInstanceEvent instance = camundaClient.newCreateInstanceCommand()
                    .bpmnProcessId(ProcessConstants.MAIN_PROCESS_ID)
                    .latestVersion()
                    .variables(variables)
                    .send()
                    .join();

            app.setProcessInstanceKey(instance.getProcessInstanceKey());
            app.setUpdatedAt(OffsetDateTime.now());
            repository.save(app);
            log.info("Started process {} (instance {}) for application {}",
                    instance.getBpmnProcessId(), instance.getProcessInstanceKey(), app.getApplicationId());
            return app;
        } catch (RuntimeException e) {
            // Do not keep an application whose workflow never started
            repository.deleteById(app.getApplicationId());
            throw new WorkflowException("Could not start the LoC workflow in Camunda: " + e.getMessage(), e);
        }
    }

    public List<LocApplication> findAll() {
        return repository.findAllNewestFirst();
    }

    public LocApplication findById(String applicationId) {
        return repository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application " + applicationId + " not found"));
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
