package com.tradefinance.loc.domain;

/** One trade document attached to an LoC application. */
public record TradeDocument(String documentType, String documentReference, String fileName) {
}
