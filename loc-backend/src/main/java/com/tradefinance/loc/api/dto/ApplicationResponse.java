package com.tradefinance.loc.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tradefinance.loc.domain.LocApplication;
import com.tradefinance.loc.domain.TradeDocument;

/**
 * Application as returned to the frontend.
 * processInstanceKey is a STRING because JavaScript cannot safely hold 64-bit numbers.
 * processVariables is only filled by GET /api/applications/{id}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationResponse(
        String applicationId,
        String status,
        String customerId,
        String applicantName,
        String applicantEmail,
        String applicantCountry,
        String beneficiaryName,
        String beneficiaryCountry,
        String beneficiaryBankName,
        BigDecimal amount,
        String currency,
        String goodsDescription,
        LocalDate latestShipmentDate,
        LocalDate expiryDate,
        List<TradeDocument> tradeDocuments,
        String processInstanceKey,
        String rejectionReason,
        String locNumber,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Map<String, Object> processVariables) {

    public static ApplicationResponse from(LocApplication a) {
        return from(a, null);
    }

    public static ApplicationResponse from(LocApplication a, Map<String, Object> processVariables) {
        return new ApplicationResponse(
                a.getApplicationId(),
                a.getStatus().name(),
                a.getCustomerId(),
                a.getApplicantName(),
                a.getApplicantEmail(),
                a.getApplicantCountry(),
                a.getBeneficiaryName(),
                a.getBeneficiaryCountry(),
                a.getBeneficiaryBankName(),
                a.getAmount(),
                a.getCurrency(),
                a.getGoodsDescription(),
                a.getLatestShipmentDate(),
                a.getExpiryDate(),
                a.getTradeDocuments(),
                a.getProcessInstanceKey() == null ? null : String.valueOf(a.getProcessInstanceKey()),
                a.getRejectionReason(),
                a.getLocNumber(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                processVariables);
    }
}
