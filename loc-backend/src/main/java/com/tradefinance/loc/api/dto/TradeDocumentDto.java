package com.tradefinance.loc.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TradeDocumentDto(
        @NotBlank
        @Pattern(regexp = "COMMERCIAL_INVOICE|BILL_OF_LADING|PACKING_LIST|CERTIFICATE_OF_ORIGIN|INSURANCE_CERTIFICATE",
                 message = "must be one of COMMERCIAL_INVOICE, BILL_OF_LADING, PACKING_LIST, CERTIFICATE_OF_ORIGIN, INSURANCE_CERTIFICATE")
        String documentType,

        @NotBlank
        String documentReference,

        String fileName) {
}
