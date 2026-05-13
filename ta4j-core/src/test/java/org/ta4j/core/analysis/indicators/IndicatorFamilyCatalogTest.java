/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class IndicatorFamilyCatalogTest {

    @Test
    public void selectDeduplicatesFamiliesWhenEnabled() {
        IndicatorFamilyCatalog catalog = catalog();

        List<String> selected = catalog.select(List.of("ema", "sma", "smaInverse", "emaFast", "close"), 3, true);

        assertThat(selected).containsExactly("ema", "smaInverse", "emaFast");
    }

    @Test
    public void selectKeepsOriginalOrderWhenFamilyLimitIsDisabled() {
        IndicatorFamilyCatalog catalog = catalog();

        List<String> selected = catalog.select(List.of("ema", "sma", "emaFast", "close"), 3);

        assertThat(selected).containsExactly("ema", "sma", "emaFast");
    }

    @Test
    public void selectIgnoresDuplicateCandidateIds() {
        IndicatorFamilyCatalog catalog = catalog();

        List<String> selected = catalog.select(List.of("ema", "sma", "ema", "smaInverse"), 3, true);

        assertThat(selected).containsExactly("ema", "smaInverse");
    }

    @Test
    public void selectValidatesMaxCount() {
        IndicatorFamilyCatalog catalog = catalog();

        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of("ema", "sma"), -1, true));
    }

    @Test
    public void selectValidatesCandidateIds() {
        IndicatorFamilyCatalog catalog = catalog();
        List<String> rankedWithNull = new ArrayList<>();
        rankedWithNull.add("ema");
        rankedWithNull.add(null);
        rankedWithNull.add("sma");

        assertThrows(IllegalArgumentException.class, () -> catalog.select(rankedWithNull, 3, true));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of("ema", " ", "sma"), 3, true));
    }

    @Test
    public void familyDriftChangedCountMustMatchChanges() {
        IndicatorFamilyCatalog.FamilyTransition transition = new IndicatorFamilyCatalog.FamilyTransition("ema",
                "family-001", "family-002");

        assertThrows(IllegalArgumentException.class,
                () -> new IndicatorFamilyCatalog.FamilyDrift("absolute", "signed", 2, List.of(transition)));
    }

    private static IndicatorFamilyCatalog catalog() {
        IndicatorFamilyAnalysisConfig config = IndicatorFamilyAnalysisConfig.defaultMode("selector");
        return new IndicatorFamilyCatalog("catalog", "manifest", "hash", config, 0,
                Map.of("ema", "family-001", "sma", "family-001", "close", "family-002", "emaFast", "family-003",
                        "smaInverse", "family-004"),
                List.of(new IndicatorFamilyCatalog.Family("family-001", List.of("ema", "sma")),
                        new IndicatorFamilyCatalog.Family("family-002", List.of("close")),
                        new IndicatorFamilyCatalog.Family("family-003", List.of("emaFast")),
                        new IndicatorFamilyCatalog.Family("family-004", List.of("smaInverse"))),
                Map.of(), "fingerprint");
    }
}
