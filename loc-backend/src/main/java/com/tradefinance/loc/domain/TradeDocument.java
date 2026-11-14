package com.tradefinance.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One trade document attached to an LoC application (table trade_document). */
@Embeddable
public record TradeDocument(
        @Column(name = "document_type", nullable = false) String documentType,
        @Column(name = "document_reference", nullable = false) String documentReference,
        @Column(name = "file_name") String fileName) {
}
