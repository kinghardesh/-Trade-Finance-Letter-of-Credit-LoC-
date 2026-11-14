package com.tradefinance.loc.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Body of POST /api/applications.
 *
 * Only FORMAT checks happen here (required fields, email format, country codes...).
 * BUSINESS checks (e.g. expiry date after shipment date) happen in the workflow,
 * in the "Validate application details" task, so the BPMN correction loop is used.
 */
public record CreateApplicationRequest(
        @NotBlank String customerId,
        @NotBlank String applicantName,
        @NotBlank @Email String applicantEmail,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO country code, e.g. DE") String applicantCountry,
        @NotBlank String beneficiaryName,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO country code, e.g. CN") String beneficiaryCountry,
        @NotBlank String beneficiaryBankName,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code, e.g. EUR") String currency,
        @NotBlank String goodsDescription,
        @NotNull LocalDate latestShipmentDate,
        @NotNull LocalDate expiryDate,
        @NotEmpty List<@Valid TradeDocumentDto> tradeDocuments,

        // Optional, for demos only: shorten the BPMN timers, e.g. "PT2M" = 2 minutes
        @Pattern(regexp = "P(T)?[0-9HMSD]+", message = "must be an ISO-8601 duration, e.g. PT2M") String correctionTimeout,
        @Pattern(regexp = "P(T)?[0-9HMSD]+", message = "must be an ISO-8601 duration, e.g. PT1M") String approvalTimeout) {
}
