package com.tradefinance.loc.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A Letter of Credit application (business data).
 * Step 5 keeps it in memory; Step 7 turns it into a JPA entity stored in PostgreSQL.
 */
public class LocApplication {

    private String applicationId;
    private String customerId;
    private String applicantName;
    private String applicantEmail;
    private String applicantCountry;
    private String beneficiaryName;
    private String beneficiaryCountry;
    private String beneficiaryBankName;
    private BigDecimal amount;
    private String currency;
    private String goodsDescription;
    private LocalDate latestShipmentDate;
    private LocalDate expiryDate;
    private List<TradeDocument> tradeDocuments = new ArrayList<>();

    private ApplicationStatus status;
    private Long processInstanceKey;
    private String rejectionReason;
    private List<String> correctionReasons = new ArrayList<>();
    private String locNumber;
    private OffsetDateTime createdAt;
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
    public void setTradeDocuments(List<TradeDocument> tradeDocuments) { this.tradeDocuments = tradeDocuments; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public Long getProcessInstanceKey() { return processInstanceKey; }
    public void setProcessInstanceKey(Long processInstanceKey) { this.processInstanceKey = processInstanceKey; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public List<String> getCorrectionReasons() { return correctionReasons; }
    public void setCorrectionReasons(List<String> correctionReasons) { this.correctionReasons = correctionReasons; }

    public String getLocNumber() { return locNumber; }
    public void setLocNumber(String locNumber) { this.locNumber = locNumber; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
