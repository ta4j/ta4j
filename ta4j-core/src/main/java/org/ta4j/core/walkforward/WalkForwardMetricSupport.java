/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WalkForwardMetricSupport {

    private WalkForwardMetricSupport() {
    }

    static <P, O> Map<String, List<WalkForwardObservation<P, O>>> groupBySnapshot(
            List<WalkForwardObservation<P, O>> observations) {
        Map<String, List<WalkForwardObservation<P, O>>> grouped = new LinkedHashMap<>();
        for (WalkForwardObservation<P, O> observation : observations) {
            String key = observation.snapshot().snapshotKey();
            grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(observation);
        }
        return grouped;
    }

    static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
