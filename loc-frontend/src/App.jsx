
import { useEffect, useMemo, useState } from "react";
import NewApplication from "./NewApplication";
import {
  Activity,
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Bell,
  BriefcaseBusiness,
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  ClipboardList,
  Clock3,
  FileCheck2,
  FilePlus2,
  FileText,
  LayoutDashboard,
  Menu,
  RefreshCw,
  Settings,
  ShieldCheck,
  Trash2,
  X,
} from "lucide-react";

import {
  getApplications,
  getApplication,
  deleteApplication,
  getTasks,
  completeTask,
} from "./services/api";

import "./index.css";

const navigation = [
  { label: "Dashboard", icon: LayoutDashboard },
  { label: "Applications", icon: BriefcaseBusiness },
  { label: "New Application", icon: FilePlus2 },
  { label: "Task Inbox", icon: ClipboardList },
];

const LIVE_REFRESH_MS = 5000;
const OPERATE_URL = "http://localhost:8080/operate";
const FINISHED_STATUSES = ["ISSUED", "REJECTED"];

const groups = [
  "trade-operations",
  "credit-managers",
  "senior-managers",
];

function getArray(data) {
  if (Array.isArray(data)) return data;

  return (
    data?.applications ??
    data?.content ??
    data?.tasks ??
    []
  );
}

function formatAmount(amount, currency = "USD") {
  if (amount === undefined || amount === null || amount === "") {
    return "—";
  }

  const number = Number(amount);

  if (!Number.isFinite(number)) {
    return `${amount} ${currency}`;
  }

  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currency || "USD",
      maximumFractionDigits: 0,
    }).format(number);
  } catch {
    return `${number.toLocaleString("en-US")} ${currency}`;
  }
}

function StatusBadge({ status }) {
  const value = (status || "UNKNOWN").toUpperCase();
  const className = value.toLowerCase().replaceAll("_", "-");

  return (
    <span className={`status-badge ${className}`}>
      <span className="status-dot" />
      {value.replaceAll("_", " ")}
    </span>
  );
}

function StatCard({ title, value, note, icon: Icon, tone }) {
  return (
    <article className="stat-card">
      <div className="stat-top">
        <span className="stat-label">{title}</span>

        <span className={`stat-icon ${tone}`}>
          <Icon size={19} />
        </span>
      </div>

      <div className="stat-value">{value}</div>
      <div className="stat-note">{note}</div>
    </article>
  );
}

// --------------------------------------------------
// TASK INBOX
// --------------------------------------------------

function TaskInbox({ tasks, onRefresh, loading }) {
  const [selectedTask, setSelectedTask] = useState(null);
  const [application, setApplication] = useState(null);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [documentReference, setDocumentReference] = useState("");
  const [fileName, setFileName] = useState("");
  const [approvalDecision, setApprovalDecision] = useState("APPROVED");
  const [approverComments, setApproverComments] = useState("");
  const [termsVerified, setTermsVerified] = useState("true");
  const [termsComments, setTermsComments] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setSelectedTask((current) => {
      if (!tasks.length) return null;
      if (current) {
        const stillOpen = tasks.find(
          (task) => task.userTaskKey === current.userTaskKey
        );
        if (stillOpen) return stillOpen;
      }
      return tasks[0];
    });
  }, [tasks]);

  useEffect(() => {
    let cancelled = false;

    async function loadApplicationDetails() {
      if (!selectedTask?.applicationId) {
        setApplication(null);
        return;
      }

      setLoadingDetails(true);
      setError("");
      setApplication(null);

      try {
        const response = await getApplication(selectedTask.applicationId);
        if (!cancelled) setApplication(response.data);
      } catch (err) {
        if (!cancelled) {
          setError(
            err.response?.data?.message ||
              "Could not load application details."
          );
        }
      } finally {
        if (!cancelled) setLoadingDetails(false);
      }
    }

    loadApplicationDetails();
    return () => {
      cancelled = true;
    };
  }, [selectedTask?.applicationId]);

  const correctionReasons =
    application?.correctionReasons ??
    application?.processVariables?.correctionReasons;

  const existingDocuments = application?.tradeDocuments ?? [];

  const isCorrectionTask =
    selectedTask?.taskDefinitionId === "Task_CorrectApplication" ||
    (selectedTask?.name || "").toLowerCase().includes("correct");

  const isManagerApprovalTask = [
    "Task_ManagerApproval",
    "Task_EscalatedApproval",
  ].includes(selectedTask?.taskDefinitionId) ||
    (selectedTask?.name || "").toLowerCase().includes("approval");

  const isTermsVerificationTask =
    selectedTask?.taskDefinitionId === "Task_VerifyTerms" ||
    (selectedTask?.name || "").toLowerCase().includes("verify loc terms");

  async function handleTermsSubmit(event) {
    event.preventDefault();
    setError("");
    setMessage("");
    if (!selectedTask?.userTaskKey) {
      setError("The selected task is missing its task key.");
      return;
    }
    try {
      setSubmitting(true);
      await completeTask(selectedTask.userTaskKey, {
        termsVerified: termsVerified === "true",
        termsComments: termsComments.trim(),
      });
      setMessage("LoC terms verification submitted. Refreshing workflow tasks...");
      setTermsComments("");
      await new Promise((resolve) => setTimeout(resolve, 2000));
      await onRefresh();
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error ||
        "Could not submit LoC terms verification. Check the backend terminal.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleApprovalSubmit(event) {
    event.preventDefault();
    setError("");
    setMessage("");

    if (!selectedTask?.userTaskKey) {
      setError("The selected task is missing its task key.");
      return;
    }

    if (!["APPROVED", "REJECTED"].includes(approvalDecision)) {
      setError("Choose Approve or Reject before submitting.");
      return;
    }

    try {
      setSubmitting(true);
      await completeTask(selectedTask.userTaskKey, {
        approvalDecision,
        approverComments: approverComments.trim(),
      });

      setMessage(
        `Decision ${approvalDecision.toLowerCase()} submitted. Refreshing workflow tasks...`
      );
      setApproverComments("");

      // Give Camunda's task search index time to reflect completion.
      await new Promise((resolve) => setTimeout(resolve, 2000));
      await onRefresh();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Could not submit the manager decision. Check the backend terminal."
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCorrectionSubmit(event) {
    event.preventDefault();
    setError("");
    setMessage("");

    if (!selectedTask?.userTaskKey || !application) {
      setError("Task or application details are missing.");
      return;
    }

    if (!documentReference.trim()) {
      setError("Enter the Bill of Lading document reference.");
      return;
    }

    if (
      existingDocuments.some(
        (document) => document.documentType === "BILL_OF_LADING"
      )
    ) {
      setError("This application already contains a Bill of Lading.");
      return;
    }

    const correctedDocuments = [
      ...existingDocuments.map((document) => ({
        documentType: document.documentType,
        documentReference: document.documentReference,
        fileName: document.fileName ?? null,
      })),
      {
        documentType: "BILL_OF_LADING",
        documentReference: documentReference.trim(),
        fileName: fileName.trim() || null,
      },
    ];

    try {
      setSubmitting(true);

      await completeTask(selectedTask.userTaskKey, {
        tradeDocuments: correctedDocuments,
      });

      setMessage("Correction submitted. Refreshing workflow tasks...");
      setDocumentReference("");
      setFileName("");

      // Allow Camunda's task search index time to reflect completion.
      await new Promise((resolve) => setTimeout(resolve, 2000));
      await onRefresh();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Could not submit the correction. Check the backend terminal."
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <section className="page-heading">
        <div>
          <div className="eyebrow">TRADE FINANCE / WORKFLOW</div>
          <h1>Task Inbox</h1>
          <p>Review and process pending Letter of Credit workflow tasks.</p>
        </div>

        <button
          className="refresh-button"
          onClick={onRefresh}
          disabled={loading || submitting}
        >
          <RefreshCw size={16} className={loading ? "spin" : ""} />
          Refresh tasks
        </button>
      </section>

      <section className="overview-grid" style={{ alignItems: "start" }}>
        <article className="panel">
          <div className="panel-heading">
            <div>
              <h2>Pending tasks</h2>
              <p>Tasks assigned to workflow teams</p>
            </div>
            <span className="task-total">{tasks.length}</span>
          </div>

          {loading ? (
            <div className="empty-state">
              <RefreshCw className="spin" size={22} />
              <span>Loading tasks...</span>
            </div>
          ) : tasks.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">
                <ClipboardList size={25} />
              </div>
              <strong>No pending tasks</strong>
              <span>New workflow tasks will appear here.</span>
            </div>
          ) : (
            <div className="task-list">
              {tasks.map((task, index) => (
                <button
                  key={task.userTaskKey || index}
                  type="button"
                  onClick={() => {
                    setSelectedTask(task);
                    setError("");
                    setMessage("");
                  }}
                  style={{
                    width: "100%",
                    display: "flex",
                    alignItems: "center",
                    gap: 12,
                    textAlign: "left",
                    padding: 15,
                    marginBottom: 10,
                    borderRadius: 10,
                    cursor: "pointer",
                    background:
                      selectedTask?.userTaskKey === task.userTaskKey
                        ? "#edf4ff"
                        : "#ffffff",
                    border:
                      selectedTask?.userTaskKey === task.userTaskKey
                        ? "2px solid #2864ad"
                        : "1px solid #e5e7eb",
                  }}
                >
                  <div className="task-icon">
                    <ClipboardList size={19} />
                  </div>
                  <div className="task-details">
                    <strong>
                      {task.name || task.taskDefinitionId || "Workflow task"}
                    </strong>
                    <span>
                      {task.applicantName || task.applicationId || "Application task"}
                    </span>
                    <small>Application: {task.applicationId || "—"}</small>
                  </div>
                  <ArrowUpRight size={16} />
                </button>
              ))}
            </div>
          )}
        </article>

        <article className="panel">
          <div className="panel-heading">
            <div>
              <h2>Task details</h2>
              <p>Selected workflow task information</p>
            </div>
          </div>

          {!selectedTask ? (
            <div className="empty-state">
              <ClipboardList size={25} />
              <strong>Select a task</strong>
              <span>Choose a pending task to see its details.</span>
            </div>
          ) : (
            <div style={{ padding: 20 }}>
              <h3>
                {selectedTask.name ||
                  selectedTask.taskDefinitionId ||
                  "Workflow task"}
              </h3>

              <p>
                <strong>Application ID:</strong>{" "}
                {selectedTask.applicationId || "—"}
              </p>
              <p>
                <strong>Applicant:</strong>{" "}
                {selectedTask.applicantName || "—"}
              </p>
              <p>
                <strong>Amount:</strong>{" "}
                {formatAmount(selectedTask.amount, selectedTask.currency)}
              </p>

              <hr />

              {loadingDetails ? (
                <p>Loading application details...</p>
              ) : error && !application ? (
                <p style={{ color: "#c0392b" }}>{error}</p>
              ) : application ? (
                <>
                  <h3>Application information</h3>
                  <p>
                    <strong>Applicant:</strong> {application.applicantName || "—"}
                  </p>
                  <p>
                    <strong>Beneficiary:</strong>{" "}
                    {application.beneficiaryName || "—"}
                  </p>
                  <p>
                    <strong>Beneficiary bank:</strong>{" "}
                    {application.beneficiaryBankName || "—"}
                  </p>
                  <p>
                    <strong>Goods:</strong>{" "}
                    {application.goodsDescription || "—"}
                  </p>
                  <p>
                    <strong>Status:</strong>{" "}
                    <StatusBadge status={application.status} />
                  </p>

                  <hr />
                  <h3>Correction reasons</h3>

                  {Array.isArray(correctionReasons) ? (
                    correctionReasons.length > 0 ? (
                      <ul>
                        {correctionReasons.map((reason, index) => (
                          <li key={index}>
                            {typeof reason === "string"
                              ? reason
                              : JSON.stringify(reason)}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p>No correction reasons were returned.</p>
                    )
                  ) : (
                    <p>
                      {correctionReasons ||
                        "No correction reasons were returned by the API."}
                    </p>
                  )}

                  <hr />
                  <h3>Existing trade documents</h3>

                  {existingDocuments.length > 0 ? (
                    <ul>
                      {existingDocuments.map((document, index) => (
                        <li key={`${document.documentType}-${index}`}>
                          <strong>{document.documentType}</strong>
                          {" — "}
                          {document.documentReference}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p>No existing documents were returned.</p>
                  )}

                  {isManagerApprovalTask && (
                    <>
                      <hr />
                      <h3>Manager decision</h3>
                      <p>
                        Review the application information and record the
                        management decision for this Letter of Credit.
                      </p>

                      <form onSubmit={handleApprovalSubmit}>
                        <div style={{ marginBottom: 15 }}>
                          <label
                            htmlFor="approvalDecision"
                            style={{
                              display: "block",
                              marginBottom: 6,
                              fontWeight: 600,
                            }}
                          >
                            Decision *
                          </label>
                          <select
                            id="approvalDecision"
                            value={approvalDecision}
                            onChange={(event) =>
                              setApprovalDecision(event.target.value)
                            }
                            required
                            style={{
                              width: "100%",
                              padding: 12,
                              border: "1px solid #d1d5db",
                              borderRadius: 8,
                              background: "#fff",
                            }}
                          >
                            <option value="APPROVED">Approve</option>
                            <option value="REJECTED">Reject</option>
                          </select>
                        </div>

                        <div style={{ marginBottom: 18 }}>
                          <label
                            htmlFor="approverComments"
                            style={{
                              display: "block",
                              marginBottom: 6,
                              fontWeight: 600,
                            }}
                          >
                            Manager comments (optional)
                          </label>
                          <textarea
                            id="approverComments"
                            value={approverComments}
                            onChange={(event) =>
                              setApproverComments(event.target.value)
                            }
                            rows={4}
                            placeholder="Add the reason or notes for this decision"
                            style={{
                              width: "100%",
                              padding: 12,
                              border: "1px solid #d1d5db",
                              borderRadius: 8,
                              font: "inherit",
                              resize: "vertical",
                            }}
                          />
                        </div>

                        <button
                          type="submit"
                          className="primary-button"
                          disabled={submitting || loadingDetails}
                        >
                          {submitting
                            ? "Submitting decision..."
                            : "Submit manager decision"}
                        </button>
                      </form>
                    </>
                  )}

                  {isTermsVerificationTask && (
                    <>
                      <hr />
                      <h3>Verify Letter of Credit terms</h3>
                      <p>
                        Compare the generated draft with the approved application
                        details and confirm whether the terms are correct.
                      </p>
                      <form onSubmit={handleTermsSubmit}>
                        <div style={{ marginBottom: 15 }}>
                          <label htmlFor="termsVerified" style={{ display: "block", marginBottom: 6, fontWeight: 600 }}>
                            Are the LoC terms correct? *
                          </label>
                          <select
                            id="termsVerified"
                            value={termsVerified}
                            onChange={(event) => setTermsVerified(event.target.value)}
                            required
                            style={{ width: "100%", padding: 12, border: "1px solid #d1d5db", borderRadius: 8, background: "#fff" }}
                          >
                            <option value="true">Yes — terms verified</option>
                            <option value="false">No — corrections required</option>
                          </select>
                        </div>
                        <div style={{ marginBottom: 18 }}>
                          <label htmlFor="termsComments" style={{ display: "block", marginBottom: 6, fontWeight: 600 }}>
                            Verification comments (optional)
                          </label>
                          <textarea
                            id="termsComments"
                            value={termsComments}
                            onChange={(event) => setTermsComments(event.target.value)}
                            rows={4}
                            placeholder="Record any observations about the draft terms"
                            style={{ width: "100%", padding: 12, border: "1px solid #d1d5db", borderRadius: 8, font: "inherit", resize: "vertical" }}
                          />
                        </div>
                        <button type="submit" className="primary-button" disabled={submitting || loadingDetails}>
                          {submitting ? "Submitting verification..." : "Submit terms verification"}
                        </button>
                      </form>
                    </>
                  )}

                  {isCorrectionTask && (
                    <>
                      <hr />
                      <h3>Add missing Bill of Lading</h3>
                      <p>
                        Add the document reference to the existing document
                        list. This form records document metadata; it does not
                        upload a PDF file.
                      </p>

                      <form onSubmit={handleCorrectionSubmit}>
                        <div style={{ marginBottom: 15 }}>
                          <label
                            htmlFor="billOfLadingReference"
                            style={{
                              display: "block",
                              marginBottom: 6,
                              fontWeight: 600,
                            }}
                          >
                            Bill of Lading reference *
                          </label>
                          <input
                            id="billOfLadingReference"
                            value={documentReference}
                            onChange={(event) =>
                              setDocumentReference(event.target.value)
                            }
                            placeholder="Enter the actual document reference"
                            required
                            style={{
                              width: "100%",
                              padding: 12,
                              border: "1px solid #d1d5db",
                              borderRadius: 8,
                            }}
                          />
                        </div>

                        <div style={{ marginBottom: 18 }}>
                          <label
                            htmlFor="billOfLadingFileName"
                            style={{
                              display: "block",
                              marginBottom: 6,
                              fontWeight: 600,
                            }}
                          >
                            File name (optional)
                          </label>
                          <input
                            id="billOfLadingFileName"
                            value={fileName}
                            onChange={(event) => setFileName(event.target.value)}
                            placeholder="bill-of-lading.pdf"
                            style={{
                              width: "100%",
                              padding: 12,
                              border: "1px solid #d1d5db",
                              borderRadius: 8,
                            }}
                          />
                        </div>

                        <button
                          type="submit"
                          className="primary-button"
                          disabled={submitting || loadingDetails}
                        >
                          {submitting
                            ? "Submitting correction..."
                            : "Submit corrections"}
                        </button>
                      </form>
                    </>
                  )}
                </>
              ) : (
                <p>Application details are not available.</p>
              )}

              {error && (
                <p style={{ color: "#c0392b", marginTop: 15 }}>{error}</p>
              )}
              {message && (
                <p style={{ color: "#16804a", marginTop: 15 }}>{message}</p>
              )}
            </div>
          )}
        </article>
      </section>
    </>
  );
}

// --------------------------------------------------
// MAIN APPLICATION
// --------------------------------------------------

// --------------------------------------------------
// APPLICATIONS PAGE
// --------------------------------------------------

function ApplicationsPage({ applications, loading, error, onRefresh }) {
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [details, setDetails] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState("");
  const [deletingId, setDeletingId] = useState("");
  const [deleteError, setDeleteError] = useState("");

  async function handleDelete(item) {
    const finished = item.status === "ISSUED" || item.status === "REJECTED";
    const message = finished
      ? `Delete ${item.applicationId}? This cannot be undone.`
      : `Delete ${item.applicationId}? Its workflow is still running and will be canceled in Camunda. This cannot be undone.`;
    if (!window.confirm(message)) return;
    setDeletingId(item.applicationId);
    setDeleteError("");
    try {
      await deleteApplication(item.applicationId);
      if (selectedId === item.applicationId) { setSelectedId(""); setDetails(null); }
      await onRefresh();
    } catch (err) {
      setDeleteError(err.response?.data?.message || `Could not delete ${item.applicationId}.`);
    } finally {
      setDeletingId("");
    }
  }

  const filteredApplications = applications.filter((item) => {
    const query = search.trim().toLowerCase();
    if (!query) return true;
    return [
      item.applicationId, item.applicantName, item.beneficiaryName,
      item.status, item.currency,
    ].some((value) => String(value || "").toLowerCase().includes(query));
  });

  async function viewDetails(applicationId) {
    setSelectedId(applicationId);
    setDetails(null);
    setDetailsError("");
    setDetailsLoading(true);
    try {
      const response = await getApplication(applicationId);
      setDetails(response.data);
    } catch (err) {
      setDetailsError(err.response?.data?.message || "Could not load application details.");
    } finally {
      setDetailsLoading(false);
    }
  }

  return (
    <>
      <section className="page-heading">
        <div>
          <div className="eyebrow">TRADE FINANCE / WORKSPACE</div>
          <h1>Applications</h1>
          <p>View and track submitted Letters of Credit.</p>
        </div>
        <button className="refresh-button" onClick={onRefresh} disabled={loading}>
          <RefreshCw size={16} className={loading ? "spin" : ""} /> Refresh applications
        </button>
      </section>

      {error && <div className="connection-alert"><CircleAlert size={19} /><div><strong>Could not load applications</strong><p>{error}</p></div></div>}
      {deleteError && <div className="form-alert error-alert"><CircleAlert size={18} /><div><strong>Delete failed</strong><p>{deleteError}</p></div></div>}

      <article className="panel applications-panel">
        <div className="panel-heading">
          <div><h2>All applications</h2><p>{applications.length} application(s) found</p></div>
          <span className="task-total">{applications.length}</span>
        </div>
        <div style={{ padding: "16px 20px", display: "flex", gap: 10, alignItems: "center" }}>
          <input aria-label="Search applications" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search by ID, applicant, beneficiary or status..." style={{ width: "100%", maxWidth: 520, padding: 12, border: "1px solid #d8dee8", borderRadius: 9, font: "inherit" }} />
        </div>
        {loading ? <div className="empty-state"><RefreshCw className="spin" size={22} /><span>Loading applications...</span></div> : filteredApplications.length === 0 ? (
          <div className="empty-state"><div className="empty-icon"><FileText size={25} /></div><strong>{applications.length ? "No matching applications" : "No applications yet"}</strong><span>{applications.length ? "Try a different search term." : "Submitted applications will appear here."}</span></div>
        ) : (
          <div className="table-wrap"><table><thead><tr><th>APPLICATION</th><th>APPLICANT</th><th>BENEFICIARY</th><th>AMOUNT</th><th>STATUS</th><th>ACTION</th></tr></thead>
            <tbody>{filteredApplications.map((item, index) => <tr key={item.applicationId || index}>
              <td><strong className="application-id">{item.applicationId || "—"}</strong><span className="table-subtext">{item.createdAt ? new Date(item.createdAt).toLocaleDateString() : "Date unavailable"}</span></td>
              <td>{item.applicantName || "—"}</td><td>{item.beneficiaryName || "—"}</td>
              <td className="amount-cell">{formatAmount(item.amount, item.currency)}</td><td><StatusBadge status={item.status} /></td>
              <td><div className="row-actions">
                <button type="button" className="text-button" onClick={() => viewDetails(item.applicationId)}>View details <ArrowUpRight size={15} /></button>
                <button type="button" className="text-button danger-button" disabled={deletingId === item.applicationId} onClick={() => handleDelete(item)} aria-label={`Delete ${item.applicationId}`}>
                  {deletingId === item.applicationId ? <RefreshCw size={14} className="spin" /> : <Trash2 size={14} />} {deletingId === item.applicationId ? "Deleting..." : "Delete"}
                </button>
              </div></td>
            </tr>)}</tbody></table></div>
        )}
      </article>

      {selectedId && <article className="panel" style={{ marginTop: 20 }}>
        <div className="panel-heading"><div><h2>Application details</h2><p>{selectedId}</p></div><button className="icon-button" aria-label="Close details" onClick={() => { setSelectedId(""); setDetails(null); }}><X size={18} /></button></div>
        <div style={{ padding: 20 }}>
          {detailsLoading ? <p>Loading application details...</p> : detailsError ? <p style={{ color: "#c0392b" }}>{detailsError}</p> : details ? (
            <>
              <div className="stats-grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))" }}>
                <StatCard title="Applicant" value={details.applicantName || "—"} note={details.applicantEmail || "Applicant"} icon={BriefcaseBusiness} tone="blue" />
                <StatCard title="Beneficiary" value={details.beneficiaryName || "—"} note={details.beneficiaryCountry || "Country unavailable"} icon={FileCheck2} tone="green" />
                <StatCard title="Amount" value={formatAmount(details.amount, details.currency)} note={details.currency || "Currency unavailable"} icon={Activity} tone="orange" />
                <StatCard title="Current status" value={<StatusBadge status={details.status} />} note={details.locNumber ? `LoC ${details.locNumber}` : "Workflow in progress"} icon={Clock3} tone="blue" />
              </div>
              <div className="overview-grid" style={{ marginTop: 20 }}>
                <div><h3>Trade information</h3><p><strong>Customer ID:</strong> {details.customerId || "—"}</p><p><strong>Beneficiary bank:</strong> {details.beneficiaryBankName || "—"}</p><p><strong>Goods:</strong> {details.goodsDescription || "—"}</p><p><strong>Applicant country:</strong> {details.applicantCountry || "—"}</p><p><strong>Latest shipment:</strong> {details.latestShipmentDate || "—"}</p><p><strong>Expiry date:</strong> {details.expiryDate || "—"}</p></div>
                <div><h3>Trade documents</h3>{Array.isArray(details.tradeDocuments) && details.tradeDocuments.length ? <ul>{details.tradeDocuments.map((doc, index) => <li key={`${doc.documentType}-${index}`}><strong>{doc.documentType}</strong> — {doc.documentReference || "No reference"}{doc.fileName ? ` (${doc.fileName})` : ""}</li>)}</ul> : <p>No trade documents returned.</p>}</div>
              </div>
              {details.correctionReasons?.length > 0 && <><h3>Correction reasons</h3><ul>{details.correctionReasons.map((reason, index) => <li key={index}>{reason}</li>)}</ul></>}
              {details.rejectionReason && <p><strong>Rejection reason:</strong> {details.rejectionReason}</p>}
            </>
          ) : null}
        </div>
      </article>}
    </>
  );
}

// --------------------------------------------------
// MAIN APPLICATION
// --------------------------------------------------

function App() {
  const [activePage, setActivePage] = useState("Dashboard");

  const [applications, setApplications] = useState([]);
  const [tasks, setTasks] = useState([]);

  const [loading, setLoading] = useState(true);
  const [connectionError, setConnectionError] = useState("");
  const [lastUpdated, setLastUpdated] = useState(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // options.silent = background refresh: no loading spinner, so the screen does not flicker
  async function loadDashboard(options) {
    const silent = options?.silent === true;
    if (!silent) setLoading(true);
    setConnectionError("");

    try {
      const [
        applicationsResponse,
        ...taskResponses
      ] = await Promise.all([
        getApplications(),
        ...groups.map((group) => getTasks(group)),
      ]);

      const applicationList = getArray(
        applicationsResponse.data
      );

      const taskList = taskResponses.flatMap((response) =>
        getArray(response.data)
      );

      setApplications(applicationList);
      setTasks(taskList);
      setLastUpdated(new Date());
    } catch (error) {
      setConnectionError(
        error.response?.data?.message ||
          "Could not connect to the backend. Check that the Spring Boot server is running on port 8081."
      );
    } finally {
      if (!silent) setLoading(false);
    }
  }

  useEffect(() => {
    loadDashboard();
  }, []);

  // LIVE dashboard: refresh every 5 s while the Dashboard is open and the browser tab is visible
  useEffect(() => {
    if (activePage !== "Dashboard") return undefined;
    const timer = setInterval(() => {
      if (document.visibilityState === "visible") loadDashboard({ silent: true });
    }, LIVE_REFRESH_MS);
    return () => clearInterval(timer);
  }, [activePage]);

  // newest workflow instances first (each application starts one Camunda process instance)
  const liveInstances = useMemo(
    () =>
      applications
        .filter((item) => item.processInstanceKey)
        .slice()
        .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
        .slice(0, 5),
    [applications]
  );

  const stats = useMemo(() => {
    const total = applications.length;

    const issued = applications.filter(
      (item) => item.status === "ISSUED"
    ).length;

    const pending = applications.filter((item) =>
      ["PENDING_APPROVAL", "ESCALATED"].includes(
        item.status
      )
    ).length;

    const rejected = applications.filter(
      (item) => item.status === "REJECTED"
    ).length;

    return {
      total,
      issued,
      pending,
      rejected,
    };
  }, [applications]);

  const recentApplications = [...applications]
    .sort((a, b) => {
      const dateA = new Date(
        a.updatedAt || a.createdAt || 0
      ).getTime();

      const dateB = new Date(
        b.updatedAt || b.createdAt || 0
      ).getTime();

      return dateB - dateA;
    })
    .slice(0, 5);

  function selectPage(label) {
    setActivePage(label);
    setSidebarOpen(false);
  }

  return (
    <div className="app-shell">
      {sidebarOpen && (
        <button
          className="sidebar-backdrop"
          aria-label="Close navigation"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* SIDEBAR */}

      <aside
        className={`sidebar ${
          sidebarOpen ? "sidebar-open" : ""
        }`}
      >
        <div className="brand">
          <div className="brand-mark">
            <FileCheck2 size={23} />
          </div>

          <div>
            <div className="brand-name">TradeFlow</div>
            <div className="brand-caption">
              TRADE FINANCE PLATFORM
            </div>
          </div>

          <button
            className="mobile-close"
            onClick={() => setSidebarOpen(false)}
            aria-label="Close menu"
          >
            <X size={20} />
          </button>
        </div>

        <div className="workspace-label">
          WORKSPACE
        </div>

        <nav className="navigation">
          {navigation.map(({ label, icon: Icon }) => (
            <button
              key={label}
              className={`nav-item ${
                activePage === label ? "active" : ""
              }`}
              onClick={() => selectPage(label)}
            >
              <Icon size={19} />

              <span>{label}</span>

              {label === "Task Inbox" &&
                tasks.length > 0 && (
                  <span className="nav-count">
                    {tasks.length}
                  </span>
                )}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="sidebar-help">
            <div className="help-icon">
              <ShieldCheck size={20} />
            </div>

            <div>
              <strong>Secure workflow</strong>
              <span>Trade finance operations</span>
            </div>
          </div>

          <button
            className="nav-item settings-item"
            onClick={() => selectPage("Settings")}
          >
            <Settings size={19} />
            <span>Settings</span>
          </button>

          <div className="profile">
            <div className="avatar">TF</div>

            <div className="profile-info">
              <strong>Trade Finance Team</strong>
              <span>Operations workspace</span>
            </div>

            <ChevronDown
              size={17}
              className="profile-chevron"
            />
          </div>
        </div>
      </aside>

      {/* MAIN CONTENT */}

      <main className="main-area">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="mobile-menu"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open navigation"
            >
              <Menu size={21} />
            </button>

            <div className="breadcrumb">
              Workspace <span>/</span>{" "}
              <strong>{activePage}</strong>
            </div>
          </div>

          <div className="topbar-actions">
            <div className="environment-tag">
              <span className="environment-dot" />
              LOCAL ENVIRONMENT
            </div>

            <button
              className="icon-button"
              aria-label="Notifications"
            >
              <Bell size={19} />
            </button>

            <div className="top-avatar">TF</div>
          </div>
        </header>

        <div className="page-content">

          {/* NEW APPLICATION */}

          {activePage === "New Application" ? (
            <NewApplication
              onBack={() => selectPage("Dashboard")}
              onSuccess={loadDashboard}
            />

          /* TASK INBOX */

          ) : activePage === "Task Inbox" ? (
            <TaskInbox
              tasks={tasks}
              onRefresh={loadDashboard}
              loading={loading}
            />

          /* APPLICATIONS */

          ) : activePage === "Applications" ? (
            <ApplicationsPage
              applications={applications}
              loading={loading}
              error={connectionError}
              onRefresh={loadDashboard}
            />

          /* DASHBOARD */

          ) : activePage === "Dashboard" ? (
            <>
              <section className="page-heading">
                <div>
                  <div className="eyebrow">
                    TRADE FINANCE / OVERVIEW
                  </div>

                  <h1>Dashboard</h1>

                  <p>
                    Monitor your Letters of Credit and
                    workflow activity.
                  </p>
                </div>

                <button
                  className="refresh-button"
                  onClick={loadDashboard}
                  disabled={loading}
                >
                  <RefreshCw
                    size={16}
                    className={loading ? "spin" : ""}
                  />
                  Refresh data
                </button>
              </section>

              {connectionError && (
                <div className="connection-alert">
                  <CircleAlert size={19} />

                  <div>
                    <strong>
                      Backend connection unavailable
                    </strong>

                    <p>{connectionError}</p>
                  </div>

                  <button onClick={loadDashboard}>
                    Retry
                  </button>
                </div>
              )}

              {/* STATISTICS */}

              <section className="stats-grid">
                <StatCard
                  title="Total applications"
                  value={loading ? "—" : stats.total}
                  note="Applications received"
                  icon={FileText}
                  tone="blue"
                />

                <StatCard
                  title="Pending approval"
                  value={loading ? "—" : stats.pending}
                  note="Awaiting management decision"
                  icon={Clock3}
                  tone="orange"
                />

                <StatCard
                  title="Letters issued"
                  value={loading ? "—" : stats.issued}
                  note="Successfully issued"
                  icon={CheckCircle2}
                  tone="green"
                />

                <StatCard
                  title="Rejected"
                  value={loading ? "—" : stats.rejected}
                  note="Applications rejected"
                  icon={ArrowDownRight}
                  tone="red"
                />
              </section>

              {/* APPLICATIONS AND TASKS */}

              <section className="overview-grid">
                <article className="panel applications-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Recent applications</h2>
                      <p>
                        Latest Letter of Credit activity
                      </p>
                    </div>

                    <button
                      className="text-button"
                      onClick={() =>
                        selectPage("Applications")
                      }
                    >
                      View all <ArrowRight size={15} />
                    </button>
                  </div>

                  {loading ? (
                    <div className="empty-state">
                      <RefreshCw
                        className="spin"
                        size={22}
                      />
                      <span>
                        Loading applications...
                      </span>
                    </div>
                  ) : recentApplications.length === 0 ? (
                    <div className="empty-state">
                      <div className="empty-icon">
                        <FileText size={25} />
                      </div>

                      <strong>No applications yet</strong>

                      <span>
                        Submitted applications will appear here.
                      </span>

                      <button
                        className="primary-button"
                        onClick={() =>
                          selectPage("New Application")
                        }
                      >
                        <FilePlus2 size={16} />
                        Create application
                      </button>
                    </div>
                  ) : (
                    <div className="table-wrap">
                      <table>
                        <thead>
                          <tr>
                            <th>APPLICATION</th>
                            <th>APPLICANT</th>
                            <th>AMOUNT</th>
                            <th>STATUS</th>
                          </tr>
                        </thead>

                        <tbody>
                          {recentApplications.map(
                            (item, index) => (
                              <tr
                                key={
                                  item.applicationId || index
                                }
                              >
                                <td>
                                  <strong className="application-id">
                                    {item.applicationId || "—"}
                                  </strong>

                                  <span className="table-subtext">
                                    {item.createdAt
                                      ? new Date(
                                          item.createdAt
                                        ).toLocaleDateString()
                                      : "Date unavailable"}
                                  </span>
                                </td>

                                <td>
                                  {item.applicantName || "—"}
                                </td>

                                <td className="amount-cell">
                                  {formatAmount(
                                    item.amount,
                                    item.currency
                                  )}
                                </td>

                                <td>
                                  <StatusBadge
                                    status={item.status}
                                  />
                                </td>
                              </tr>
                            )
                          )}
                        </tbody>
                      </table>
                    </div>
                  )}
                </article>

                <article className="panel tasks-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Workflow tasks</h2>
                      <p>Open tasks across teams</p>
                    </div>

                    <span className="task-total">
                      {loading ? "—" : tasks.length}
                    </span>
                  </div>

                  {loading ? (
                    <div className="empty-state compact">
                      Loading tasks...
                    </div>
                  ) : tasks.length === 0 && liveInstances.length > 0 ? (
                    <p className="empty-inline">No open tasks right now.</p>
                  ) : tasks.length === 0 ? (
                    <div className="empty-state compact">
                      <div className="empty-icon">
                        <ClipboardList size={24} />
                      </div>

                      <strong>No open tasks</strong>

                      <span>
                        New workflow tasks will appear here.
                      </span>
                    </div>
                  ) : (
                    <div className="task-list">
                      {tasks.slice(0, 5).map(
                        (task, index) => (
                          <div
                            className="task-row"
                            key={task.userTaskKey || index}
                          >
                            <div className="task-icon">
                              <ClipboardList size={18} />
                            </div>

                            <div className="task-details">
                              <strong>
                                {task.name ||
                                  task.taskDefinitionId ||
                                  "Workflow task"}
                              </strong>

                              <span>
                                {task.applicantName ||
                                  task.applicationId ||
                                  "Application task"}
                              </span>

                              {task.processInstanceKey && (
                                <span className="instance-key">
                                  Instance {task.processInstanceKey}
                                </span>
                              )}
                            </div>

                            <ArrowUpRight
                              size={16}
                              className="task-arrow"
                            />
                          </div>
                        )
                      )}
                    </div>
                  )}

                  {!loading && liveInstances.length > 0 && (
                    <div className="instance-section">
                      <div className="instance-heading">
                        <strong>Live process instances</strong>
                        <span className="live-pill">
                          <span /> LIVE
                        </span>
                      </div>

                      {liveInstances.map((item) => {
                        const running = !FINISHED_STATUSES.includes(item.status);
                        return (
                          <a
                            key={item.processInstanceKey}
                            className="instance-row"
                            href={`${OPERATE_URL}/processes/${item.processInstanceKey}`}
                            target="_blank"
                            rel="noreferrer"
                            title="Open this process instance in Camunda Operate"
                          >
                            <span
                              className={`instance-dot ${running ? "running" : "finished"}`}
                            />
                            <span className="instance-text">
                              <code>{item.processInstanceKey}</code>
                              <span>{item.applicationId}</span>
                            </span>
                            <StatusBadge status={item.status} />
                          </a>
                        );
                      })}
                    </div>
                  )}

                  <button
                    className="inbox-link"
                    onClick={() => selectPage("Task Inbox")}
                  >
                    Open task inbox <ArrowRight size={15} />
                  </button>
                </article>
              </section>

              {/* FOOTER STATUS */}

              <section className="bottom-strip">
                <div className="bottom-strip-icon">
                  <Activity size={20} />
                </div>

                <div>
                  <strong>Workflow monitoring</strong>

                  <p>
                    {lastUpdated
                      ? `Data last refreshed at ${lastUpdated.toLocaleTimeString()}.`
                      : "Refresh the dashboard to check the latest workflow activity."}
                  </p>
                </div>

                <div className="bottom-strip-spacer" />

                <span className="live-label">
                  <span /> API STATUS
                </span>
              </section>
            </>

          /* OTHER PAGES */

          ) : (
            <section className="placeholder-page">
              <div className="placeholder-icon">
                {activePage === "Applications" ? (
                  <BriefcaseBusiness size={28} />
                ) : (
                  <Settings size={28} />
                )}
              </div>

              <div className="eyebrow">
                TRADE FINANCE WORKSPACE
              </div>

              <h1>{activePage}</h1>

              <p>
                This section is the next part of the
                application build. The dashboard and
                backend API connection are set up first.
              </p>

              <button
                className="primary-button"
                onClick={() => selectPage("Dashboard")}
              >
                <LayoutDashboard size={16} />
                Back to dashboard
              </button>
            </section>
          )}

          <footer className="footer">
            <span>
              TradeFlow{" "}
              <span className="footer-separator">•</span>{" "}
              Letter of Credit Workflow Automation
            </span>

            <span>
              College Project{" "}
              <span className="footer-separator">•</span>{" "}
              Local Development
            </span>
          </footer>
        </div>
      </main>
    </div>
  );
}

export default App;