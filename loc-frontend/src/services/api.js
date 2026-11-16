
import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8081",
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 15000,
});

export const getApplications = () =>
  api.get("/api/applications");

export const getApplication = (applicationId) =>
  api.get(`/api/applications/${applicationId}`);

// Status timeline, oldest first: [{ status, changedAt }]
export const getApplicationHistory = (applicationId) =>
  api.get(`/api/applications/${applicationId}/history`);

export const submitApplication = (applicationData) =>
  api.post("/api/applications", applicationData);

// Deletes the application; a still-running workflow is canceled in Camunda first
export const deleteApplication = (applicationId) =>
  api.delete(`/api/applications/${applicationId}`);

export const getTasks = (candidateGroup, applicationId) =>
  api.get("/api/tasks", {
    params: {
      ...(candidateGroup && { candidateGroup }),
      ...(applicationId && { applicationId }),
    },
  });

export const completeTask = (userTaskKey, variables) =>
  api.post(`/api/tasks/${userTaskKey}/complete`, { variables });

export default api;
