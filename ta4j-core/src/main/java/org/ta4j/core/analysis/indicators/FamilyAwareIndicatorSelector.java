/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility for choosing a deterministic family-aware indicator set from a ranked
 * list.
 *
 * @since 0.22.7
 */
public final class FamilyAwareIndicatorSelector {

    private FamilyAwareIndicatorSelector() {
    }

    /**
     * Selects ranked indicators and optionally enforces a maximum of one indicator
     * per family.
     *
     * @param rankedIndicatorIds ranked indicator ids, ordered by preference
     * @param catalog            family catalog used for family lookups
     * @param maxCount           maximum indicators to return
     * @param enforceFamilyLimit true to reject duplicate-family picks when possible
     * @return deterministic selected ids
     * @since 0.22.7
     */
    public static List<String> select(List<String> rankedIndicatorIds, IndicatorFamilyCatalog catalog, int maxCount,
            boolean enforceFamilyLimit) {
        Objects.requireNonNull(rankedIndicatorIds, "rankedIndicatorIds");
        if (maxCount < 0) {
            throw new IllegalArgumentException("maxCount must be >= 0");
        }

        if (maxCount == 0 || rankedIndicatorIds.isEmpty()) {
            return List.of();
        }

        Map<String, String> familyByIndicator = catalog == null ? Map.of() : catalog.familyByIndicator();
        List<String> selected = new ArrayList<>(Math.min(maxCount, rankedIndicatorIds.size()));
        Set<String> usedFamilies = new HashSet<>();
        Set<String> usedIndicators = new LinkedHashSet<>();

        for (String indicatorId : rankedIndicatorIds) {
            if (selected.size() >= maxCount) {
                break;
            }
            if (!usedIndicators.add(indicatorId)) {
                continue;
            }

            if (!enforceFamilyLimit || catalog == null) {
                selected.add(indicatorId);
                continue;
            }

            String familyId = familyByIndicator.get(indicatorId);
            if (familyId == null) {
                selected.add(indicatorId);
            } else if (usedFamilies.add(familyId)) {
                selected.add(indicatorId);
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Selects ranked indicators with family dedupe disabled.
     *
     * @param rankedIndicatorIds ranked indicator ids, ordered by preference
     * @param catalog            family catalog (unused)
     * @param maxCount           maximum indicators to return
     * @return deterministic selected ids
     * @since 0.22.7
     */
    public static List<String> select(List<String> rankedIndicatorIds, IndicatorFamilyCatalog catalog, int maxCount) {
        return select(rankedIndicatorIds, catalog, maxCount, false);
    }
}
