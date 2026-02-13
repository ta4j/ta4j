/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.risk;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * R-multiple criterion based on per-trade risk.
 *
 * <p>
 * Computes {@code profit / risk} for each closed position using the provided
 * {@link PositionRiskModel}. For a trading record, the criterion returns the
 * average R-multiple across closed positions with valid risk values.
 *
 * @since 0.22.2
 */
public class RMultipleCriterion extends AbstractAnalysisCriterion {

    private final PositionRiskModel riskModel;

    /**
     * Constructor.
     *
     * @param riskModel model that provides per-trade risk amounts
     */
    public RMultipleCriterion(PositionRiskModel riskModel) {
        if (riskModel == null) {
            throw new IllegalArgumentException("riskModel must not be null");
        }
        this.riskModel = riskModel;
    }

    /**
     * Calculates R-multiple for a single closed position.
     *
     * @param series   the bar series
     * @param position the position to evaluate
     * @return {@code profit / risk} for the position, or zero when unavailable
     * @since 0.22.2
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        Num zero = series.numFactory().zero();
        if (position == null || !position.isClosed()) {
            return zero;
        }
        Num risk = riskModel.risk(series, position);
        if (risk == null || risk.isZero() || risk.isNegative()) {
            return zero;
        }
        return position.getProfit().dividedBy(risk);
    }

    /**
     * Calculates average R-multiple over all closed positions in a trading record.
     *
     * @param series        the bar series
     * @param tradingRecord the trading record to evaluate
     * @return mean R-multiple across positions with valid positive risk
     * @since 0.22.2
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num zero = series.numFactory().zero();
        if (tradingRecord == null) {
            return zero;
        }
        Num sum = zero;
        int count = 0;
        for (Position position : tradingRecord.getPositions()) {
            if (position == null || !position.isClosed()) {
                continue;
            }
            Num risk = riskModel.risk(series, position);
            if (risk == null || risk.isZero() || risk.isNegative()) {
                continue;
            }
            sum = sum.plus(position.getProfit().dividedBy(risk));
            count++;
        }
        if (count == 0) {
            return zero;
        }
        return sum.dividedBy(series.numFactory().numOf(count));
    }

    /**
     * Indicates that higher R-multiples are better.
     *
     * @param criterionValue1 first value
     * @param criterionValue2 second value
     * @return {@code true} when first value is greater
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
