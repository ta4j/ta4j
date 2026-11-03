/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Criterion outside {@code org.ta4j.core.criteria} that records whether
 * deserialization initialized or constructed it. Tests must only reference
 * this class through {@code ExternalCriterionProbe.class} and {@link Probe},
 * so it stays uninitialized unless deserialization initializes it.
 */
public final class ExternalCriterionProbe implements AnalysisCriterion {

    static {
        Probe.initialized = true;
    }

    public ExternalCriterionProbe() {
        Probe.constructed = true;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return series.numFactory().zero();
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    static final class Probe {

        static boolean initialized;
        static boolean constructed;

        private Probe() {
        }
    }
}
