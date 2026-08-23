/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityBundle;
import org.ta4j.core.num.Num;

/**
 * Hook for criteria that can evaluate against a shared {@link EquityBundle}
 * instead of constructing their own equity curves.
 *
 * <p>
 * Implementations must return the same value
 * {@code calculate(series, tradingRecord)} would return; the bundle only
 * removes redundant curve construction when several criteria are evaluated
 * together via
 * {@link CriteriaEvaluation#evaluateAll(BarSeries, TradingRecord, AnalysisCriterion...)}.
 * Criteria that do not implement this interface fall back to their regular
 * two-argument calculation in batch evaluation.
 * </p>
 *
 * @since 0.24.2
 */
public interface EquityBundleAware {

    /**
     * Calculates this criterion using curves shared via the given bundle.
     *
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @param equityBundle  the shared equity bundle, not null
     * @return the criterion value, identical to
     *         {@code calculate(series, tradingRecord)}
     */
    Num calculate(BarSeries series, TradingRecord tradingRecord, EquityBundle equityBundle);
}
