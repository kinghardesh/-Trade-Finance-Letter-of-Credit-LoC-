package com.tradefinance.loc.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * A Letter of Credit application, stored in PostgreSQL table loc_application
 * (schema: src/main/resources/db/migration/V1__initial_schema.sql).
 *
 * {@code @DynamicUpdate}: UPDATE statements only contain the columns that changed, so the
 * job workers (status) and the REST API (corrections) never overwrite each other's changes.
 */
@Entity
@Table(name = "loc_application")
@DynamicUpdate
public class LocApplication {

    @Id
    @Column(name = "application_id", length = 30)
    private String applicationId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "applicant_name", nullable = false)
    private String applicantName;

    @Column(name = "applicant_email", nullable = false)
    private String applicantEmail;

    @Column(name = "applicant_country", nullable = false, length = 2)
    private String applicantCountry;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "beneficiary_country", nullable = false, length = 2)
    private String beneficiaryCountry;

    @Column(name = "beneficiary_bank_name", nullable = false)
    private String beneficiaryBankName;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "goods_description", nullable = false)
    private String goodsDescription;

    @Column(name = "latest_shipment_date", nullable = false)
    private LocalDate latestShipmentDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trade_document", joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "sort_order")
    private List<TradeDocument> tradeDocuments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(name = "process_instance_key")
    private Long processInstanceKey;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "application_correction_reason", joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "reason", nullable = false)
    private List<String> correctionReasons = new ArrayList<>();

    @Column(name = "loc_number")
    private String locNumber;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }

    public String getApplicantEmail() { return applicantEmail; }
    public void setApplicantEmail(String applicantEmail) { this.applicantEmail = applicantEmail; }

    public String getApplicantCountry() { return applicantCountry; }
    public void setApplicantCountry(String applicantCountry) { this.applicantCountry = applicantCountry; }

    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }

    public String getBeneficiaryCountry() { return beneficiaryCountry; }
    public void setBeneficiaryCountry(String beneficiaryCountry) { this.beneficiaryCountry = beneficiaryCountry; }

    public String getBeneficiaryBankName() { return beneficiaryBankName; }
    public void setBeneficiaryBankName(String beneficiaryBankName) { this.beneficiaryBankName = beneficiaryBankName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getGoodsDescription() { return goodsDescription; }
    public void setGoodsDescription(String goodsDescription) { this.goodsDescription = goodsDescription; }

    public LocalDate getLatestShipmentDate() { return latestShipmentDate; }
    public void setLatestShipmentDate(LocalDate latestShipmentDate) { this.latestShipmentDate = latestShipmentDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public List<TradeDocument> getTradeDocuments() { return tradeDocuments; }
    public void setTradeDocuments(List<TradeDocument> tradeDocuments) {
        this.tradeDocuments.clear();
        if (tradeDocuments != null) {
            this.tradeDocuments.addAll(tradeDocuments);
        }
    }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public Long getProcessInstanceKey() { return processInstanceKey; }
    public void setProcessInstanceKey(Long processInstanceKey) { this.processInstanceKey = processInstanceKey; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public List<String> getCorrectionReasons() { return correctionReasons; }
    public void setCorrectionReasons(List<String> correctionReasons) {
        this.correctionReasons.clear();
        if (correctionReasons != null) {
            this.correctionReasons.addAll(correctionReasons);
        }
    }

    public String getLocNumber() { return locNumber; }
    public void setLocNumber(String locNumber) { this.locNumber = locNumber; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
