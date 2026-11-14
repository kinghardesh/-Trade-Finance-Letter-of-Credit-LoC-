package com.tradefinance.loc.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * BUSINESS validation used by the "Validate application details" task.
 * (Field format checks already happened in the REST API.)
 * Rules, inspired by common documentary-credit practice (UCP 600):
 *   1. latest shipment date must not be in the past
 *   2. expiry date at least 21 days after latest shipment date (presentation period)
 *   3. applicant and beneficiary must be in different countries (international trade)
 *   4. amount must not exceed 50,000,000
 *   5. documents must include a COMMERCIAL_INVOICE and a BILL_OF_LADING
 */
@Component
public class ApplicationValidator {

    static final int PRESENTATION_PERIOD_DAYS = 21;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000");
    static final Set<String> REQUIRED_DOCUMENTS = Set.of("COMMERCIAL_INVOICE", "BILL_OF_LADING");

    public List<String> validate(String latestShipmentDate, String expiryDate, String applicantCountry,
                                 String beneficiaryCountry, BigDecimal amount,
                                 List<Map<String, Object>> tradeDocuments, LocalDate today) {
        List<String> errors = new ArrayList<>();

        LocalDate shipment = parse(latestShipmentDate, "latestShipmentDate", errors);
        LocalDate expiry = parse(expiryDate, "expiryDate", errors);

        if (shipment != null && shipment.isBefore(today)) {
            errors.add("latestShipmentDate must not be in the past");
        }
        if (shipment != null && expiry != null && expiry.isBefore(shipment.plusDays(PRESENTATION_PERIOD_DAYS))) {
            errors.add("expiryDate must be at least " + PRESENTATION_PERIOD_DAYS
                    + " days after latestShipmentDate (presentation period)");
        }
        if (applicantCountry != null && applicantCountry.equals(beneficiaryCountry)) {
            errors.add("applicant and beneficiary must be in different countries");
        }
        if (amount != null && amount.compareTo(MAX_AMOUNT) > 0) {
            errors.add("amount exceeds the maximum of 50,000,000 for this product");
        }

        Set<String> types = tradeDocuments == null ? Set.of()
                : tradeDocuments.stream()
                        .map(d -> Objects.toString(d.get("documentType"), ""))
                        .collect(Collectors.toSet());
        REQUIRED_DOCUMENTS.stream()
                .filter(t -> !types.contains(t))
                .sorted()
                .forEach(t -> errors.add("required document missing: " + t));

        return errors;
    }

    private LocalDate parse(String value, String field, List<String> errors) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(field + " is not a valid date (YYYY-MM-DD)");
            return null;
        }
    }
}
