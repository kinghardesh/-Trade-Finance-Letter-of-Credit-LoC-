import { useEffect, useState } from "react";
import { ArrowUpRight, CircleAlert, RefreshCw, X } from "lucide-react";

import { getApplication, getApplicationHistory } from "./services/api";

const OPERATE_URL = "http://localhost:8080/operate";

const DOCUMENT_LABELS = {
  COMMERCIAL_INVOICE: "Commercial invoice",
  BILL_OF_LADING: "Bill of lading",
  PACKING_LIST: "Packing list",
  CERTIFICATE_OF_ORIGIN: "Certificate of origin",
  INSURANCE_CERTIFICATE: "Insurance certificate",
};

function formatStatus(status) {
  return status ? status.replaceAll("_", " ") : "—";
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString(undefined, { day: "2-digit", month: "short", year: "numeric" });
}

function formatDateTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function formatAmount(amount, currency) {
  if (amount === null || amount === undefined) return "—";
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency: currency || "USD" }).format(amount);
  } catch {
    return `${amount} ${currency || ""}`.trim();
  }
}

function Field({ label, value, wide }) {
  return (
    <div className={`detail-field${wide ? " wide" : ""}`}>
      <dt>{label}</dt>
      <dd>{value === null || value === undefined || value === "" ? "—" : value}</dd>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <section className="detail-section">
      <h3>{title}</h3>
      {children}
    </section>
  );
}

/**
 * Pop-up with everything the user entered for one application,
 * plus where its workflow is now and the status timeline.
 */
export default function ApplicationDetails({ applicationId, onClose }) {
  const [details, setDetails] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const [detailsResponse, historyResponse] = await Promise.all([
          getApplication(applicationId),
          getApplicationHistory(applicationId),
        ]);
        if (cancelled) return;
        setDetails(detailsResponse.data);
        setHistory(Array.isArray(historyResponse.data) ? historyResponse.data : []);
      } catch (err) {
        if (!cancelled) setError(err.response?.data?.message || "Could not load application details.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [applicationId]);

  // Escape closes the pop-up
  useEffect(() => {
    function onKey(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const variables = details?.processVariables || {};
  const documents = Array.isArray(details?.tradeDocuments) ? details.tradeDocuments : [];

  return (
    <div className="details-overlay" onClick={onClose}>
      <div
        className="details-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="details-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="details-header">
          <div>
            <div className="eyebrow">APPLICATION DETAILS</div>
            <h2 id="details-title">{applicationId}</h2>
            {details && (
              <p>
                Submitted {formatDateTime(details.createdAt)}
                {details.updatedAt ? ` · last updated ${formatDateTime(details.updatedAt)}` : ""}
              </p>
            )}
          </div>
          <div className="details-header-right">
            {details && (
              <span className={`status-badge ${(details.status || "unknown").toLowerCase().replaceAll("_", "-")}`}>
                <span className="status-dot" />
                {formatStatus(details.status)}
              </span>
            )}
            <button className="icon-button" aria-label="Close details" onClick={onClose}>
              <X size={18} />
            </button>
          </div>
        </header>

        <div className="details-body">
          {loading ? (
            <div className="details-message">
              <RefreshCw size={18} className="spin" /> Loading application details...
            </div>
          ) : error ? (
            <div className="form-alert error-alert">
              <CircleAlert size={18} />
              <div>
                <strong>Could not load details</strong>
                <p>{error}</p>
              </div>
            </div>
          ) : details ? (
            <>
              {details.rejectionReason && (
                <div className="form-alert error-alert">
                  <CircleAlert size={18} />
                  <div>
                    <strong>Rejected</strong>
                    <p>{details.rejectionReason}</p>
                  </div>
                </div>
              )}
              {details.correctionReasons?.length > 0 && details.status === "CORRECTION_REQUIRED" && (
                <div className="form-alert warning-alert">
                  <CircleAlert size={18} />
                  <div>
                    <strong>Correction required</strong>
                    <ul>
                      {details.correctionReasons.map((reason, index) => (
                        <li key={index}>{reason}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              )}

              <Section title="Applicant">
                <dl className="detail-grid">
                  <Field label="Customer ID" value={details.customerId} />
                  <Field label="Name" value={details.applicantName} />
                  <Field label="Email" value={details.applicantEmail} />
                  <Field label="Country" value={details.applicantCountry} />
                </dl>
              </Section>

              <Section title="Beneficiary">
                <dl className="detail-grid">
                  <Field label="Name" value={details.beneficiaryName} />
                  <Field label="Country" value={details.beneficiaryCountry} />
                  <Field label="Advising bank" value={details.beneficiaryBankName} wide />
                </dl>
              </Section>

              <Section title="Letter of Credit">
                <dl className="detail-grid">
                  <Field label="Amount" value={formatAmount(details.amount, details.currency)} />
                  <Field label="Currency" value={details.currency} />
                  <Field label="Latest shipment date" value={formatDate(details.latestShipmentDate)} />
                  <Field label="Expiry date" value={formatDate(details.expiryDate)} />
                  <Field label="Goods description" value={details.goodsDescription} wide />
                </dl>
              </Section>

              <Section title={`Trade documents (${documents.length})`}>
                {documents.length ? (
                  <div className="table-wrap">
                    <table className="details-table">
                      <thead>
                        <tr><th>TYPE</th><th>REFERENCE</th><th>FILE</th></tr>
                      </thead>
                      <tbody>
                        {documents.map((doc, index) => (
                          <tr key={`${doc.documentType}-${index}`}>
                            <td>{DOCUMENT_LABELS[doc.documentType] || doc.documentType}</td>
                            <td>{doc.documentReference || "—"}</td>
                            <td>{doc.fileName || "—"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="details-muted">No trade documents.</p>
                )}
              </Section>

              <Section title="Workflow">
                <dl className="detail-grid">
                  <Field
                    label="Process instance key"
                    value={
                      details.processInstanceKey ? (
                        <a
                          className="instance-link"
                          href={`${OPERATE_URL}/processes/${details.processInstanceKey}`}
                          target="_blank"
                          rel="noreferrer"
                        >
                          {details.processInstanceKey} <ArrowUpRight size={13} />
                        </a>
                      ) : null
                    }
                  />
                  <Field label="LoC number" value={details.locNumber} />
                  <Field label="Draft" value={variables.draftReference} />
                  <Field label="Manager decision" value={variables.approvalDecision} />
                  <Field label="Manager comments" value={variables.approverComments} wide />
                  <Field
                    label="Terms verified"
                    value={
                      variables.termsVerified === undefined ? null : variables.termsVerified ? "Yes" : "No"
                    }
                  />
                  <Field label="Terms comments" value={variables.termsComments} />
                </dl>
              </Section>

              <Section title="Status history">
                {history.length ? (
                  <ol className="status-timeline">
                    {history.map((entry, index) => (
                      <li key={`${entry.status}-${index}`} className={index === history.length - 1 ? "current" : ""}>
                        <span className="timeline-dot" />
                        <strong>{formatStatus(entry.status)}</strong>
                        <span>{formatDateTime(entry.changedAt)}</span>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <p className="details-muted">No status changes recorded.</p>
                )}
              </Section>
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}
