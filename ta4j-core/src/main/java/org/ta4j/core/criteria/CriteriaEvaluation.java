/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityBundle;
import org.ta4j.core.num.Num;

/**
 * Batch evaluation of several {@link AnalysisCriterion analysis criteria} over
 * one trading record.
 *
 * <p>
 * Evaluating criteria one by one rebuilds the identical equity cash flow for
 * every equity-curve-based criterion. Batch evaluation builds each distinct
 * curve once and shares it across all participating criteria, which support
 * this transparently. Criteria without shared-curve support are evaluated as if
 * {@link AnalysisCriterion#calculate(BarSeries, TradingRecord) calculate} had
 * been called directly, so arbitrary criterion mixes produce exactly the same
 * values as individual evaluation.
 * </p>
 *
 * @since 0.24.2
 */
public final class CriteriaEvaluation {

    private CriteriaEvaluation() {
    }

    /**
     * Evaluates all given criteria over the trading record, sharing equity curves
     * between criteria that support it.
     *
     * @param series        the bar series to analyze, not null
     * @param tradingRecord the trading record to analyze, not null
     * @param criteria      the criteria to evaluate, not null
     * @return an insertion-ordered map from criterion to value; duplicate criterion
     *         instances collapse onto their first entry
     * @since 0.24.2
     */
    public static Map<AnalysisCriterion, Num> evaluateAll(BarSeries series, TradingRecord tradingRecord,
            Collection<? extends AnalysisCriterion> criteria) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(criteria, "criteria cannot be null");

        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);
        Map<AnalysisCriterion, Num> results = new LinkedHashMap<>();
        for (AnalysisCriterion criterion : criteria) {
            Objects.requireNonNull(criterion, "criteria must not contain null");
            if (results.containsKey(criterion)) {
                continue;
            }
            Num value = criterion instanceof EquityBundleAware bundleAware
                    ? bundleAware.calculate(series, tradingRecord, equityBundle)
                    : criterion.calculate(series, tradingRecord);
            results.put(criterion, value);
        }
        return Collections.unmodifiableMap(results);
    }

    /**
     * Evaluates all given criteria over the trading record, sharing equity curves
     * between criteria that support it.
     *
     * @param series        the bar series to analyze, not null
     * @param tradingRecord the trading record to analyze, not null
     * @param criteria      the criteria to evaluate, not null
     * @return an insertion-ordered map from criterion to value; duplicate criterion
     *         instances collapse onto their first entry
     * @since 0.24.2
     */
    public static Map<AnalysisCriterion, Num> evaluateAll(BarSeries series, TradingRecord tradingRecord,
            AnalysisCriterion... criteria) {
        return evaluateAll(series, tradingRecord, Arrays.asList(criteria));
    }
}
