
import { useState } from "react";
import { ArrowLeft, FilePlus2, Plus, Trash2, Send, AlertCircle, CheckCircle2 } from "lucide-react";
import { submitApplication } from "./services/api";

const documentTypes = [
  "COMMERCIAL_INVOICE",
  "BILL_OF_LADING",
  "PACKING_LIST",
  "CERTIFICATE_OF_ORIGIN",
  "INSURANCE_CERTIFICATE",
];

const initialForm = {
  customerId: "",
  applicantName: "",
  applicantEmail: "",
  applicantCountry: "",
  beneficiaryName: "",
  beneficiaryCountry: "",
  beneficiaryBankName: "",
  amount: "",
  currency: "USD",
  goodsDescription: "",
  latestShipmentDate: "",
  expiryDate: "",
  tradeDocuments: [
    { documentType: "COMMERCIAL_INVOICE", documentReference: "", fileName: "" },
  ],
};

function Field({ label, name, value, onChange, required = false, ...props }) {
  return (
    <label className="form-field">
      <span>{label}{required && <b className="required-mark"> *</b>}</span>
      <input
        name={name}
        value={value}
        onChange={onChange}
        required={required}
        {...props}
      />
    </label>
  );
}

function SectionHeading({ number, title, description }) {
  return (
    <div className="form-section-heading">
      <span className="section-number">{number}</span>
      <div>
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function NewApplication({ onBack, onSuccess }) {
  const [form, setForm] = useState(initialForm);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState([]);
  const [success, setSuccess] = useState(null);

  function updateField(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  function updateDocument(index, field, value) {
    setForm((current) => ({
      ...current,
      tradeDocuments: current.tradeDocuments.map((document, i) =>
        i === index ? { ...document, [field]: value } : document
      ),
    }));
  }

  function addDocument() {
    setForm((current) => ({
      ...current,
      tradeDocuments: [
        ...current.tradeDocuments,
        { documentType: "COMMERCIAL_INVOICE", documentReference: "", fileName: "" },
      ],
    }));
  }

  function removeDocument(index) {
    setForm((current) => ({
      ...current,
      tradeDocuments: current.tradeDocuments.filter((_, i) => i !== index),
    }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setFieldErrors([]);
    setSuccess(null);

    const applicantCountry = form.applicantCountry.trim().toUpperCase();
    const beneficiaryCountry = form.beneficiaryCountry.trim().toUpperCase();
    const currency = form.currency.trim().toUpperCase();

    if (!/^[A-Z]{2}$/.test(applicantCountry)) {
      setError("Applicant country must be a 2-letter ISO country code, such as IN.");
      return;
    }

    if (!/^[A-Z]{2}$/.test(beneficiaryCountry)) {
      setError("Beneficiary country must be a 2-letter ISO country code, such as US.");
      return;
    }

    if (!/^[A-Z]{3}$/.test(currency)) {
      setError("Currency must be a 3-letter code, such as USD, EUR or INR.");
      return;
    }

    if (Number(form.amount) <= 0 || !Number.isFinite(Number(form.amount))) {
      setError("Please enter an amount greater than zero.");
      return;
    }

    if (form.latestShipmentDate && form.expiryDate &&
        form.expiryDate < form.latestShipmentDate) {
      setError("Expiry date cannot be earlier than the latest shipment date.");
      return;
    }

    if (
      form.tradeDocuments.length === 0 ||
      form.tradeDocuments.some((doc) => !doc.documentReference.trim())
    ) {
      setError("Add at least one trade document and provide its document reference.");
      return;
    }

    const payload = {
      ...form,
      applicantCountry,
      beneficiaryCountry,
      currency,
      amount: Number(form.amount),
      tradeDocuments: form.tradeDocuments.map((doc) => ({
        documentType: doc.documentType,
        documentReference: doc.documentReference.trim(),
        ...(doc.fileName.trim() ? { fileName: doc.fileName.trim() } : {}),
      })),
    };

    setSubmitting(true);

    try {
      const response = await submitApplication(payload);
      setSuccess(response.data);
      setForm(initialForm);
      if (onSuccess) onSuccess(response.data);
    } catch (err) {
      const data = err.response?.data;
      setError(
        data?.message ||
        err.message ||
        "Application submission failed. Please check the backend and try again."
      );

      if (data?.fieldErrors) {
        setFieldErrors(
          Array.isArray(data.fieldErrors)
            ? data.fieldErrors
            : Object.entries(data.fieldErrors).map(([field, message]) => ({
                field,
                message,
              }))
        );
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="form-page">
      <div className="form-page-heading">
        <div>
          <button className="back-button" type="button" onClick={onBack}>
            <ArrowLeft size={16} /> Back to dashboard
          </button>
          <div className="eyebrow">TRADE FINANCE / APPLICATIONS</div>
          <h1>New Letter of Credit</h1>
          <p>Enter the details below to initiate a Letter of Credit workflow.</p>
        </div>
        <div className="form-heading-icon"><FilePlus2 size={25} /></div>
      </div>

      {error && (
        <div className="form-alert error-alert">
          <AlertCircle size={18} />
          <div>
            <strong>Unable to submit application</strong>
            <p>{error}</p>
            {fieldErrors.map((item, index) => (
              <p key={index}>{item.field}: {item.message}</p>
            ))}
          </div>
        </div>
      )}

      {success && (
        <div className="form-alert success-alert">
          <CheckCircle2 size={19} />
          <div>
            <strong>Application submitted successfully!</strong>
            <p>
              Application ID: {success.applicationId || "Created"}
              {" · "}Status: {success.status || "Submitted"}
            </p>
            <button
              type="button"
              className="success-link"
              onClick={onBack}
            >
              Return to dashboard
            </button>
          </div>
        </div>
      )}

      <form className="application-form" onSubmit={handleSubmit}>
        <section className="form-section">
          <SectionHeading
            number="01"
            title="Applicant information"
            description="Details of the customer requesting the Letter of Credit."
          />
          <div className="form-grid">
            <Field label="Customer ID" name="customerId" value={form.customerId} onChange={updateField} required placeholder="e.g. CUST-1001" />
            <Field label="Applicant name" name="applicantName" value={form.applicantName} onChange={updateField} required placeholder="Full legal name" />
            <Field label="Applicant email" name="applicantEmail" type="email" value={form.applicantEmail} onChange={updateField} required placeholder="name@company.com" />
            <Field label="Applicant country code" name="applicantCountry" value={form.applicantCountry} onChange={updateField} required maxLength={2} placeholder="IN" />
          </div>
        </section>

        <section className="form-section">
          <SectionHeading
            number="02"
            title="Beneficiary information"
            description="Details of the party receiving payment under the Letter of Credit."
          />
          <div className="form-grid">
            <Field label="Beneficiary name" name="beneficiaryName" value={form.beneficiaryName} onChange={updateField} required placeholder="Full legal name" />
            <Field label="Beneficiary country code" name="beneficiaryCountry" value={form.beneficiaryCountry} onChange={updateField} required maxLength={2} placeholder="US" />
            <Field label="Beneficiary bank name" name="beneficiaryBankName" value={form.beneficiaryBankName} onChange={updateField} required placeholder="Bank name" />
          </div>
        </section>

        <section className="form-section">
          <SectionHeading
            number="03"
            title="Letter of Credit details"
            description="Financial and commercial terms of the proposed transaction."
          />
          <div className="form-grid">
            <Field label="Amount" name="amount" type="number" min="0.01" step="0.01" value={form.amount} onChange={updateField} required placeholder="100000" />
            <Field label="Currency code" name="currency" value={form.currency} onChange={updateField} required maxLength={3} placeholder="USD" />
            <Field label="Latest shipment date" name="latestShipmentDate" type="date" value={form.latestShipmentDate} onChange={updateField} required />
            <Field label="Expiry date" name="expiryDate" type="date" value={form.expiryDate} onChange={updateField} required />
            <label className="form-field full-width">
              <span>Goods description <b className="required-mark">*</b></span>
              <textarea
                name="goodsDescription"
                value={form.goodsDescription}
                onChange={updateField}
                required
                rows={4}
                placeholder="Describe the goods covered by this Letter of Credit..."
              />
            </label>
          </div>
        </section>

        <section className="form-section documents-section">
          <div className="documents-heading">
            <SectionHeading
              number="04"
              title="Trade documents"
              description="Provide at least one document reference for this application."
            />
            <button type="button" className="add-document-button" onClick={addDocument}>
              <Plus size={15} /> Add document
            </button>
          </div>

          <div className="document-note">
            <AlertCircle size={16} />
            This version records document references and optional file names. It does not upload actual files.
          </div>

          {form.tradeDocuments.map((document, index) => (
            <div className="document-card" key={index}>
              <div className="document-card-heading">
                <strong>Document {index + 1}</strong>
                {form.tradeDocuments.length > 1 && (
                  <button
                    type="button"
                    className="remove-document-button"
                    onClick={() => removeDocument(index)}
                    aria-label={`Remove document ${index + 1}`}
                  >
                    <Trash2 size={15} /> Remove
                  </button>
                )}
              </div>
              <div className="form-grid">
                <label className="form-field">
                  <span>Document type <b className="required-mark">*</b></span>
                  <select
                    value={document.documentType}
                    onChange={(event) => updateDocument(index, "documentType", event.target.value)}
                    required
                  >
                    {documentTypes.map((type) => (
                      <option value={type} key={type}>
                        {type.replaceAll("_", " ")}
                      </option>
                    ))}
                  </select>
                </label>
                <Field
                  label="Document reference"
                  value={document.documentReference}
                  onChange={(event) => updateDocument(index, "documentReference", event.target.value)}
                  required
                  placeholder="e.g. INV-2026-001"
                />
                <Field
                  label="File name (optional)"
                  value={document.fileName}
                  onChange={(event) => updateDocument(index, "fileName", event.target.value)}
                  placeholder="invoice.pdf"
                />
              </div>
            </div>
          ))}
        </section>

        <div className="form-actions">
          <button type="button" className="cancel-button" onClick={onBack}>
            Cancel
          </button>
          <button type="submit" className="submit-button" disabled={submitting}>
            <Send size={16} />
            {submitting ? "Submitting..." : "Submit application"}
          </button>
        </div>
      </form>
    </div>
  );
}
