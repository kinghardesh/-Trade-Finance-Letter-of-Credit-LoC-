package com.tradefinance.loc.mock;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * MOCK sanctions list screening (no real list provider).
 * Demo rules: a party matches when its name contains "BLOCKED" or "SANCTIONED",
 * or when its country is an embargoed jurisdiction (KP, IR, SY).
 */
@Component
public class MockSanctionsList {

    private static final Set<String> EMBARGOED_COUNTRIES = Set.of("KP", "IR", "SY");

    public record ScreeningResult(boolean matchFound, int matchScore, String listName) {
    }

    public ScreeningResult screen(String name, String country) {
        String upper = name == null ? "" : name.toUpperCase(Locale.ROOT);
        if (upper.contains("BLOCKED") || upper.contains("SANCTIONED")) {
            return new ScreeningResult(true, 98, "DEMO-CONSOLIDATED-LIST");
        }
        if (country != null && EMBARGOED_COUNTRIES.contains(country)) {
            return new ScreeningResult(true, 100, "DEMO-EMBARGO-LIST");
        }
        return new ScreeningResult(false, 0, null);
    }
}
