/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.List;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.Returns;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Computes the Omega ratio.
 *
 * <p>
 * <b>Definition.</b> For returns {@code r_i} and threshold {@code tau}:
 *
 * <pre>
 * Omega(tau) = sum(max(r_i - tau, 0)) / sum(max(tau - r_i, 0))
 * </pre>
 *
 * <p>
 * The numerator aggregates upside excess returns above the threshold, and the
 * denominator aggregates downside shortfalls below the threshold.
 *
 * <p>
 * <b>Implementation details.</b> This criterion reuses {@link Returns} with
 * {@link ReturnRepresentation#DECIMAL} to obtain the return series used for the
 * ratio.
 *
 * <p>
 * <b>Open positions:</b> When using {@link EquityCurveMode#MARK_TO_MARKET}, the
 * {@link OpenPositionHandling} setting controls whether open positions
 * contribute to return observations. {@link EquityCurveMode#REALIZED} always
 * ignores open positions regardless of the requested handling.
 *
 * <p>
 * If downside shortfall is zero and upside excess exists, the theoretical ratio
 * is unbounded; this implementation returns {@link NaN#NaN}. If both upside and
 * downside are zero, it returns zero.
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Omega_ratio">https://en.wikipedia.org/wiki/Omega_ratio</a>
 * @since 0.22.2
 */
public class OmegaRatioCriterion extends AbstractEquityCurveSettingsCriterion {

    private final double threshold;

    /**
     * Constructor with zero threshold.
     *
     * @since 0.22.2
     */
    public OmegaRatioCriterion() {
        this(0d, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor with explicit threshold.
     *
     * @param threshold return threshold in decimal form (for example {@code 0.01}
     *                  for 1%)
     * @since 0.22.2
     */
    public OmegaRatioCriterion(double threshold) {
        this(threshold, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using a specific equity curve calculation mode.
     *
     * @param equityCurveMode the equity curve mode to use
     * @since 0.22.2
     */
    public OmegaRatioCriterion(EquityCurveMode equityCurveMode) {
        this(0d, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using the provided open position handling.
     *
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public OmegaRatioCriterion(OpenPositionHandling openPositionHandling) {
        this(0d, EquityCurveMode.MARK_TO_MARKET, openPositionHandling);
    }

    /**
     * Constructor using specific threshold, equity curve, and open position
     * handling.
     *
     * @param threshold            return threshold in decimal form
     * @param equityCurveMode      the equity curve mode to use
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public OmegaRatioCriterion(double threshold, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        super(equityCurveMode, openPositionHandling);
        this.threshold = threshold;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory numFactory = series.numFactory();
        if (position == null || position.getEntry() == null) {
            return numFactory.zero();
        }
        return calculate(series, new BaseTradingRecord(position));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory numFactory = series.numFactory();
        Num zero = numFactory.zero();
        if (tradingRecord == null || series.isEmpty()) {
            return zero;
        }

        int beginIndex = tradingRecord.getStartIndex(series);
        int endIndex = tradingRecord.getEndIndex(series);
        if (endIndex <= beginIndex) {
            return zero;
        }

        Returns returns = new Returns(series, tradingRecord, ReturnRepresentation.DECIMAL, equityCurveMode,
                openPositionHandling);
        Num thresholdNum = numFactory.numOf(threshold);
        Num upsideExcess = zero;
        Num downsideShortfall = zero;

        List<Num> returnRates = returns.getRawValues();
        for (int i = beginIndex + 1; i <= endIndex; i++) {
            Num returnRate = returnRates.get(i);
            if (returnRate.isNaN()) {
                continue;
            }

            Num excess = returnRate.minus(thresholdNum);
            if (excess.isGreaterThan(zero)) {
                upsideExcess = upsideExcess.plus(excess);
            } else if (excess.isLessThan(zero)) {
                downsideShortfall = downsideShortfall.plus(excess.negate());
            }
        }

        if (downsideShortfall.isZero()) {
            return upsideExcess.isZero() ? zero : NaN.NaN;
        }
        return upsideExcess.dividedBy(downsideShortfall);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
