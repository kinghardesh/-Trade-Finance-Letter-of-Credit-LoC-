package com.tradefinance.loc.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradefinance.loc.api.dto.ApplicationResponse;
import com.tradefinance.loc.api.dto.CreateApplicationRequest;
import com.tradefinance.loc.api.dto.StatusHistoryResponse;
import com.tradefinance.loc.domain.LocApplication;
import com.tradefinance.loc.service.ApplicationService;

import jakarta.validation.Valid;

/**
 * LoC applications.
 *   POST /api/applications          submit a new application (starts the workflow)
 *   GET  /api/applications          list all applications (newest first)
 *   GET  /api/applications/{id}     one application + its current workflow variables
 *   GET  /api/applications/{id}/history   status timeline
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> submit(@Valid @RequestBody CreateApplicationRequest request) {
        LocApplication app = applicationService.submit(request);
        return ResponseEntity
                .created(URI.create("/api/applications/" + app.getApplicationId()))
                .body(ApplicationResponse.from(app));
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return applicationService.findAll().stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/{applicationId}")
    public ApplicationResponse get(@PathVariable String applicationId) {
        LocApplication app = applicationService.findById(applicationId);
        return ApplicationResponse.from(app, applicationService.findProcessVariables(app));
    }

    /** Status timeline from table application_status_history, oldest first. */
    @GetMapping("/{applicationId}/history")
    public List<StatusHistoryResponse> history(@PathVariable String applicationId) {
        return applicationService.findHistory(applicationId).stream().map(StatusHistoryResponse::from).toList();
    }
}
