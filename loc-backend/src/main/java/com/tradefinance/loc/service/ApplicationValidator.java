package com.tradefinance.loc.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
            errors.add("Latest shipment date %s is in the past (today is %s) - choose %s or later"
                    .formatted(shipment, today, today));
        }
        if (shipment != null && expiry != null && expiry.isBefore(shipment.plusDays(PRESENTATION_PERIOD_DAYS))) {
            long days = ChronoUnit.DAYS.between(shipment, expiry);
            errors.add(("Expiry date %s is only %d days after latest shipment date %s - it must be at least %d days "
                    + "(presentation period), so the earliest allowed expiry date is %s")
                    .formatted(expiry, days, shipment, PRESENTATION_PERIOD_DAYS,
                            shipment.plusDays(PRESENTATION_PERIOD_DAYS)));
        }
        if (applicantCountry != null && applicantCountry.equals(beneficiaryCountry)) {
            errors.add("Applicant and beneficiary are both in %s - a Letter of Credit is for international trade, "
                    .formatted(applicantCountry) + "so they must be in different countries");
        }
        if (amount != null && amount.compareTo(MAX_AMOUNT) > 0) {
            errors.add("Amount %s exceeds the maximum of 50,000,000 for this product"
                    .formatted(amount.toPlainString()));
        }

        Set<String> types = tradeDocuments == null ? Set.of()
                : tradeDocuments.stream()
                        .map(d -> Objects.toString(d.get("documentType"), ""))
                        .collect(Collectors.toSet());
        REQUIRED_DOCUMENTS.stream()
                .filter(t -> !types.contains(t))
                .sorted()
                .forEach(t -> errors.add("Required document missing: " + t + " - add it to the trade documents"));

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
