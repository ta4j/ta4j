/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.criteria;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.SampleSummary;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.IndexPair;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.analysis.frequency.SamplingFrequencyIndexPairs;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.TimeConstants;

/**
 * Computes the Sharpe Ratio.
 *
 * <p>
 * <b>Definition.</b> The Sharpe Ratio is defined as {@code SR = μ / σ}, where
 * {@code μ} is the expected value of excess returns and {@code σ} is the
 * standard deviation of excess returns.
 *
 * <p>
 * <b>What this criterion measures.</b> This implementation builds a time series
 * of <em>excess returns</em> from the {@link CashFlow} equity curve. For each
 * sampled pair {@code (previousIndex, currentIndex)}, it compounds per-bar
 * excess growth factors between the two indices (so mixed in/out-of-market bars
 * are handled correctly) and converts the compounded growth into an excess
 * return. It then returns {@code mean(excessReturn) / stdev(excessReturn)}
 * using the sample standard deviation.
 *
 * <p>
 * <b>Sampling (aggregation) of returns.</b> The {@link SamplingFrequency}
 * parameter controls how the return series is formed:
 * <ul>
 * <li>{@link SamplingFrequency#BAR}: one return per bar, using consecutive bar
 * indices.</li>
 * <li>{@link SamplingFrequency#SECOND}/{@link SamplingFrequency#MINUTE}/{@link SamplingFrequency#HOUR}/{@link SamplingFrequency#DAY}/{@link SamplingFrequency#WEEK}/{@link SamplingFrequency#MONTH}:
 * returns are computed between period endpoints detected from bar
 * {@code endTime} after converting it to {@link #groupingZoneId}. Period
 * boundaries follow ISO week semantics for {@code WEEKLY}.</li>
 * </ul>
 * The first sampled return is anchored at the series begin index (for
 * {@link TradingRecord}) or the entry index (for {@link Position}), so the
 * first period return spans from the anchor to the first period end.
 *
 * <p>
 * <b>Risk-free rate.</b> {@link #annualRiskFreeRate} is interpreted as an
 * annualized rate (e.g., 0.05 = 5% per year) and converted into a per-bar
 * compounded growth factor using the elapsed time between bar end times. If
 * {@code annualRiskFreeRate} is {@code null}, it is treated as zero.
 *
 * <p>
 * <b>Cash return policy.</b> {@link CashReturnPolicy#CASH_EARNS_RISK_FREE}
 * makes flat equity intervals benchmark-neutral (approx. zero excess), while
 * {@link CashReturnPolicy#CASH_EARNS_ZERO} treats flat equity as
 * underperforming cash and contributes negative excess returns.
 *
 * <p>
 * <b>Annualization.</b> When {@link Annualization#PERIOD}, the returned Sharpe
 * is per sampling period (no scaling). When {@link Annualization#ANNUALIZED},
 * the per-period Sharpe is multiplied by {@code sqrt(periodsPerYear)} where
 * {@code periodsPerYear} is estimated from observed time deltas (count of
 * positive deltas divided by the sum of deltas in years).
 *
 * <p>
 * <b>Trading record vs. position.</b> Sharpe ratio requires a distribution of
 * returns across periods/positions, so it is defined for {@link TradingRecord}.
 * A single {@link Position} does not provide a return distribution; therefore,
 * {@link #calculate(BarSeries, Position)} intentionally returns zero.
 *
 * <p>
 * <b>Open positions.</b> When {@link #openPositionHandling} is
 * {@link OpenPositionHandling#MARK_TO_MARKET}, the current open position (if
 * any) contributes to both invested intervals and cash-flow accrual. When
 * {@link OpenPositionHandling#IGNORE}, only closed positions are considered for
 * the return series and position count.
 *
 * @since 0.22.1
 *
 */
public class SharpeRatioCriterion extends AbstractAnalysisCriterion {

    public enum Annualization {
        PERIOD, ANNUALIZED
    }

    private final SamplingFrequencyIndexPairs samplingFrequencyIndexPairs;
    private final Annualization annualization;
    private final CashReturnPolicy cashReturnPolicy;
    private final double annualRiskFreeRate;
    private final ZoneId groupingZoneId;
    private final OpenPositionHandling openPositionHandling;

    public SharpeRatioCriterion() {
        this(0, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC, CashReturnPolicy.CASH_EARNS_RISK_FREE,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    public SharpeRatioCriterion(double annualRiskFreeRate) {
        this(annualRiskFreeRate, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, OpenPositionHandling.MARK_TO_MARKET);
    }

    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates a Sharpe ratio criterion with explicit cash return handling.
     *
     * @param annualRiskFreeRate the annual risk-free rate (e.g. 0.05 for 5%)
     * @param samplingFrequency  the sampling granularity
     * @param annualization      the annualization mode
     * @param groupingZoneId     the time zone used to interpret bar end times
     * @param cashReturnPolicy   the policy for flat equity intervals
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId, cashReturnPolicy,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates a Sharpe ratio criterion with explicit cash return handling.
     *
     * @param annualRiskFreeRate   the annual risk-free rate (e.g. 0.05 for 5%)
     * @param samplingFrequency    the sampling granularity
     * @param annualization        the annualization mode
     * @param groupingZoneId       the time zone used to interpret bar end times
     * @param cashReturnPolicy     the policy for flat equity intervals
     * @param openPositionHandling how open positions should be handled
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy,
            OpenPositionHandling openPositionHandling) {
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.annualization = Objects.requireNonNull(annualization, "annualization must not be null");
        this.groupingZoneId = Objects.requireNonNull(groupingZoneId, "groupingZoneId must not be null");
        this.cashReturnPolicy = Objects.requireNonNull(cashReturnPolicy, "cashReturnPolicy must not be null");
        this.openPositionHandling = Objects.requireNonNull(openPositionHandling,
                "openPositionHandling must not be null");
        Objects.requireNonNull(samplingFrequency, "samplingFrequency must not be null");
        this.samplingFrequencyIndexPairs = new SamplingFrequencyIndexPairs(samplingFrequency, this.groupingZoneId);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        // Sharpe needs a distribution of returns across periods/positions; a single
        // position is intentionally treated as neutral.
        return series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (hasInsufficientPositions(tradingRecord, openPositionHandling)) {
            return series.numFactory().zero();
        }

        var start = series.getBeginIndex() + 1;
        var end = series.getEndIndex();
        var anchorIndex = series.getBeginIndex();
        return calculateSharpe(series, tradingRecord, anchorIndex, start, end);
    }

    private static boolean hasInsufficientPositions(TradingRecord tradingRecord,
            OpenPositionHandling openPositionHandling) {
        var tradingRecordMissing = tradingRecord == null;
        if (tradingRecordMissing) {
            return true;
        }
        var openPositionCount = openPositionHandling == OpenPositionHandling.MARK_TO_MARKET
                && tradingRecord.getCurrentPosition().isOpened() ? 1 : 0;
        return tradingRecord.getPositionCount() + openPositionCount < 2;
    }

    private Num calculateSharpe(BarSeries series, TradingRecord tradingRecord, int anchorIndex, int start, int end) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        if (end - start + 1 < 2) {
            return zero;
        }
        var excessReturns = new ExcessReturns(series, series.numFactory().numOf(annualRiskFreeRate), cashReturnPolicy,
                tradingRecord, openPositionHandling);
        Stream<IndexPair> pairs = samplingFrequencyIndexPairs.sample(series, anchorIndex, start, end);

        var summary = SampleSummary.fromSamples(pairs.map(pair -> new SampleSummary.Sample(
                excessReturns.excessReturn(pair.previousIndex(), pair.currentIndex()),
                deltaYears(series, pair.previousIndex(), pair.currentIndex()))), numFactory);

        if (summary.count() < 2) {
            return zero;
        }

        var stdev = summary.sampleVariance(numFactory).sqrt();
        if (stdev.isZero()) {
            return zero;
        }

        var sharpePerPeriod = summary.mean().dividedBy(stdev);

        if (annualization == Annualization.PERIOD) {
            return sharpePerPeriod;
        }

        return summary.annualizationFactor(numFactory)
                .map(factor -> sharpePerPeriod.multipliedBy(factor))
                .orElse(sharpePerPeriod);
    }

    private Num deltaYears(BarSeries series, int previousIndex, int currentIndex) {
        var endPrev = endTimeInstant(series, previousIndex);
        var endNow = endTimeInstant(series, currentIndex);
        var seconds = Math.max(0, Duration.between(endPrev, endNow).getSeconds());
        var numFactory = series.numFactory();
        return seconds <= 0 ? numFactory.zero()
                : numFactory.numOf(seconds).dividedBy(numFactory.numOf(TimeConstants.SECONDS_PER_YEAR));
    }

    private Instant endTimeInstant(BarSeries series, int index) {
        return series.getBar(index).getEndTime();
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

}
