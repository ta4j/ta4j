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

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.*;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.BarSeriesUtils;

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
 * The first sampled return is anchored at the series begin index, even when
 * evaluating a single {@link Position}, so the first period return spans from
 * the series start to the first period end. Pre-entry intervals are handled by
 * {@link CashReturnPolicy}: {@link CashReturnPolicy#CASH_EARNS_RISK_FREE} keeps
 * flat pre-entry equity neutral, while {@link CashReturnPolicy#CASH_EARNS_ZERO}
 * treats flat pre-entry equity as underperforming cash.
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
 * returns across periods. A single {@link Position} can still provide multiple
 * sampled excess returns when the series spans multiple bars, so
 * {@link #calculate(BarSeries, Position)} now evaluates the position using the
 * same sampling logic as a trading record.
 *
 * <p>
 * <b>Open positions.</b> When {@link #openPositionHandling} is
 * {@link OpenPositionHandling#MARK_TO_MARKET}, the current open position (if
 * any) contributes to both invested intervals and cash-flow accrual. When
 * {@link OpenPositionHandling#IGNORE}, only closed positions are considered for
 * the return series and position count. When {@link #equityCurveMode} is
 * {@link EquityCurveMode#REALIZED}, open positions are ignored regardless of
 * {@link #openPositionHandling}, so interim price movements do not affect
 * returns.
 *
 * @since 0.22.2
 *
 */
public class SharpeRatioCriterion extends AbstractAnalysisCriterion {

    public enum Annualization {
        PERIOD, ANNUALIZED
    }

    private final SamplingFrequencyIndexes samplingFrequencyIndexes;
    private final Annualization annualization;
    private final CashReturnPolicy cashReturnPolicy;
    private final double annualRiskFreeRate;
    private final ZoneId groupingZoneId;
    private final EquityCurveMode equityCurveMode;
    private final OpenPositionHandling openPositionHandling;

    /**
     * Creates a Sharpe ratio criterion using a zero risk-free rate, per-bar
     * sampling, annualized scaling, UTC grouping, and
     * {@link CashReturnPolicy#CASH_EARNS_RISK_FREE}.
     *
     * @since 0.22.2
     */
    public SharpeRatioCriterion() {
        this(0, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC, CashReturnPolicy.CASH_EARNS_RISK_FREE,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates a Sharpe ratio criterion with a custom annual risk-free rate, per-bar
     * sampling, annualized scaling, UTC grouping, and
     * {@link CashReturnPolicy#CASH_EARNS_RISK_FREE}.
     *
     * @param annualRiskFreeRate the annual risk-free rate (e.g. 0.05 for 5%)
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate) {
        this(annualRiskFreeRate, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates a Sharpe ratio criterion with explicit sampling, annualization, and
     * grouping timezone.
     *
     * @param annualRiskFreeRate the annual risk-free rate (e.g. 0.05 for 5%)
     * @param samplingFrequency  the sampling granularity
     * @param annualization      the annualization mode
     * @param groupingZoneId     the time zone used to interpret bar end times
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
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
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
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
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId, cashReturnPolicy,
                EquityCurveMode.MARK_TO_MARKET, openPositionHandling);
    }

    /**
     * Creates a Sharpe ratio criterion with explicit cash return handling.
     *
     * @param annualRiskFreeRate   the annual risk-free rate (e.g. 0.05 for 5%)
     * @param samplingFrequency    the sampling granularity
     * @param annualization        the annualization mode
     * @param groupingZoneId       the time zone used to interpret bar end times
     * @param cashReturnPolicy     the policy for flat equity intervals
     * @param equityCurveMode      the equity curve calculation mode
     * @param openPositionHandling how open positions should be handled
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.annualization = Objects.requireNonNull(annualization, "annualization must not be null");
        this.groupingZoneId = Objects.requireNonNull(groupingZoneId, "groupingZoneId must not be null");
        this.cashReturnPolicy = Objects.requireNonNull(cashReturnPolicy, "cashReturnPolicy must not be null");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode, "equityCurveMode must not be null");
        this.openPositionHandling = Objects.requireNonNull(openPositionHandling,
                "openPositionHandling must not be null");
        Objects.requireNonNull(samplingFrequency, "samplingFrequency must not be null");
        this.samplingFrequencyIndexes = new SamplingFrequencyIndexes(samplingFrequency, this.groupingZoneId);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return series.numFactory().zero();
        }

        return calculate(series, new BaseTradingRecord(position));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        if (tradingRecord == null) {
            return zero;
        }

        int beginIndex = series.getBeginIndex();
        var start = beginIndex + 1;
        var end = series.getEndIndex();
        if (end - start + 1 < 2) {
            return zero;
        }
        var annualRiskFreeRateNum = numFactory.numOf(annualRiskFreeRate);
        var excessReturns = new ExcessReturns(series, annualRiskFreeRateNum, cashReturnPolicy, tradingRecord,
                equityCurveMode, openPositionHandling);
        var samples = samplingFrequencyIndexes.sample(series, beginIndex, start, end)
                .map(pair -> getSample(series, pair, excessReturns));
        var summary = SampleSummary.fromSamples(samples, numFactory);

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

        return summary.annualizationFactor(numFactory).map(sharpePerPeriod::multipliedBy).orElse(sharpePerPeriod);
    }

    private Sample getSample(BarSeries series, IndexPair pair, ExcessReturns excessReturns) {
        var previousIndex = pair.previousIndex();
        var excessReturn = excessReturns.excessReturn(previousIndex, pair.currentIndex());
        var deltaYears = BarSeriesUtils.deltaYears(series, previousIndex, pair.currentIndex());
        return new Sample(excessReturn, deltaYears);
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

}
