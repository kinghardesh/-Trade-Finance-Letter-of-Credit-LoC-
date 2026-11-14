package com.tradefinance.loc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradefinance.loc.api.dto.CompleteTaskRequest;
import com.tradefinance.loc.api.dto.TaskResponse;
import com.tradefinance.loc.service.TaskService;

/**
 * User tasks (the React "inbox").
 *   GET  /api/tasks?candidateGroup=credit-managers&applicationId=LOC-APP-2026-0001   (both optional)
 *   POST /api/tasks/{userTaskKey}/complete   body: { "variables": { ... } }
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<TaskResponse> list(@RequestParam(required = false) String candidateGroup,
                                   @RequestParam(required = false) String applicationId) {
        return taskService.findOpenTasks(candidateGroup, applicationId);
    }

    @PostMapping("/{userTaskKey}/complete")
    public ResponseEntity<Void> complete(@PathVariable long userTaskKey,
                                         @RequestBody(required = false) CompleteTaskRequest request) {
        taskService.complete(userTaskKey, request == null ? java.util.Map.of() : request.variablesOrEmpty());
        return ResponseEntity.noContent().build();
    }
}
