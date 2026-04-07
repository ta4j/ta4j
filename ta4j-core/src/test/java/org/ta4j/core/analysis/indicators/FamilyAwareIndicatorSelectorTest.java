/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class FamilyAwareIndicatorSelectorTest {

    @Test
    public void deduplicatesFamiliesWhenEnabled() {
        IndicatorFamilyCatalog catalog = catalog();

        List<String> selected = FamilyAwareIndicatorSelector
                .select(List.of("ema", "sma", "smaInverse", "emaFast", "close"), catalog, 3, true);
        assertThat(selected).containsExactly("ema", "smaInverse", "emaFast");
    }

    @Test
    public void keepsOriginalOrderWhenDisabled() {
        IndicatorFamilyCatalog catalog = catalog();

        List<String> selected = FamilyAwareIndicatorSelector.select(List.of("ema", "sma", "emaFast", "close"), catalog,
                3);
        assertThat(selected).containsExactly("ema", "sma", "emaFast");
    }

    @Test
    public void rejectsDuplicateCandidateIds() {
        IndicatorFamilyCatalog catalog = catalog();

        List<String> selected = FamilyAwareIndicatorSelector.select(List.of("ema", "sma", "ema", "smaInverse"), catalog,
                3, true);
        assertThat(selected).containsExactly("ema", "smaInverse");
    }

    @Test
    public void validatesMaxCount() {
        IndicatorFamilyCatalog catalog = catalog();

        assertThrows(IllegalArgumentException.class,
                () -> FamilyAwareIndicatorSelector.select(List.of("ema", "sma"), catalog, -1, true));
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
