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

import java.time.ZoneOffset;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;
import java.util.Objects;
import org.ta4j.core.analysis.frequency.SamplingFrequencyIndexPairs;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

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
 *  <b>Trading record vs. position.</b> Sharpe ratio requires a distribution of
 *  returns across periods/positions, so it is defined for {@link TradingRecord}.
 *  A single {@link Position} does not provide a return distribution; therefore,
 *  {@link #calculate(BarSeries, Position)} intentionally returns zero.
 *
 * @since 0.22.1
 *
 */
public class SharpeRatioCriterion extends AbstractAnalysisCriterion {

    private static final double SECONDS_PER_YEAR = 365.2425d * 24 * 3600;

    public enum Annualization {
        PERIOD, ANNUALIZED
    }

    private final SamplingFrequencyIndexPairs samplingFrequencyIndexPairs;
    private final Annualization annualization;
    private final CashReturnPolicy cashReturnPolicy;
    private final double annualRiskFreeRate;
    private final ZoneId groupingZoneId;

    public SharpeRatioCriterion() {
        this(0, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC, CashReturnPolicy.CASH_EARNS_RISK_FREE);
    }

    public SharpeRatioCriterion(double annualRiskFreeRate) {
        this(annualRiskFreeRate, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE);
    }

    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
                                Annualization annualization, ZoneId groupingZoneId) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId,
                CashReturnPolicy.CASH_EARNS_RISK_FREE);
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
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.annualization = Objects.requireNonNull(annualization, "annualization must not be null");
        this.groupingZoneId = Objects.requireNonNull(groupingZoneId, "groupingZoneId must not be null");
        this.cashReturnPolicy = Objects.requireNonNull(cashReturnPolicy, "cashReturnPolicy must not be null");
        this.samplingFrequencyIndexPairs = new SamplingFrequencyIndexPairs(samplingFrequency, this.groupingZoneId);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        // Sharpe needs a distribution of returns across periods/positions; a single position
        // is intentionally treated as neutral.
        return series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        if (tradingRecord == null) {
            return zero;
        }

        var hasClosedPositions = tradingRecord.getPositions().stream().anyMatch(Position::isClosed);
        if (!hasClosedPositions) {
            return zero;
        }

        var start = series.getBeginIndex() + 1;
        var end = series.getEndIndex();
        var anchorIndex = series.getBeginIndex();
        return calculateSharpe(series, tradingRecord, anchorIndex, start, end);
    }

    private Num calculateSharpe(BarSeries series, TradingRecord tradingRecord, int anchorIndex, int start, int end) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        if (end - start + 1 < 2) {
            return zero;
        }
        var excessReturns = new ExcessReturns(series, series.numFactory().numOf(annualRiskFreeRate), cashReturnPolicy,
                tradingRecord);
        Stream<SamplingFrequencyIndexPairs.IndexPair> pairs = samplingFrequencyIndexPairs.sample(series, anchorIndex,
                start, end);

        var acc = pairs.reduce(Acc.empty(zero),
                (a, p) -> a.add(excessReturns.excessReturn(p.previousIndex(), p.currentIndex()),
                        deltaYears(series, p.previousIndex(), p.currentIndex()), numFactory),
                (a, b) -> a.merge(b, numFactory));

        if (acc.stats().count() < 2) {
            return zero;
        }

        var stdev = acc.stats().sampleVariance(numFactory).sqrt();
        if (stdev.isZero()) {
            return zero;
        }

        var sharpePerPeriod = acc.stats().mean().dividedBy(stdev);

        if (annualization == Annualization.PERIOD) {
            return sharpePerPeriod;
        }

        var annualizationFactor = acc.annualizationFactor(numFactory);
        if (annualizationFactor.isLessThanOrEqual(zero)) {
            return sharpePerPeriod;
        }

        return sharpePerPeriod.multipliedBy(annualizationFactor);
    }

    private Num deltaYears(BarSeries series, int previousIndex, int currentIndex) {
        var endPrev = endTimeInstant(series, previousIndex);
        var endNow = endTimeInstant(series, currentIndex);
        var seconds = Math.max(0, Duration.between(endPrev, endNow).getSeconds());
        var numFactory = series.numFactory();
        return seconds <= 0 ? numFactory.zero()
                : numFactory.numOf(seconds).dividedBy(numFactory.numOf(SECONDS_PER_YEAR));
    }

    private Instant endTimeInstant(BarSeries series, int index) {
        return series.getBar(index).getEndTime();
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

    private record Acc(Stats stats, Num deltaYearsSum, Num deltaCount) {

        static Acc empty(Num zero) {
            return new Acc(Stats.empty(zero), zero, zero);
        }

        Acc add(Num excessReturn, Num deltaYears, NumFactory numFactory) {
            var nextStats = stats.add(excessReturn, numFactory);
            if (deltaYears.isLessThanOrEqual(numFactory.zero())) {
                return new Acc(nextStats, deltaYearsSum, deltaCount);
            }
            return new Acc(nextStats, deltaYearsSum.plus(deltaYears), deltaCount.plus(numFactory.one()));
        }

        Acc merge(Acc other, NumFactory numFactory) {
            var mergedStats = stats.merge(other.stats, numFactory);
            return new Acc(mergedStats, deltaYearsSum.plus(other.deltaYearsSum), deltaCount.plus(other.deltaCount));
        }

        Num annualizationFactor(NumFactory numFactory) {
            var zero = numFactory.zero();
            if (deltaCount.isLessThanOrEqual(zero) || deltaYearsSum.isLessThanOrEqual(zero)) {
                return zero;
            }
            return deltaCount.dividedBy(deltaYearsSum).sqrt();
        }
    }

    record Stats(Num mean, Num m2, int count) {

        static Stats empty(Num zero) {
            return new Stats(zero, zero, 0);
        }

        Stats add(Num x, NumFactory f) {
            if (count == 0) {
                return new Stats(x, f.zero(), 1);
            }
            var n = count + 1;
            var nNum = f.numOf(n);
            var delta = x.minus(mean);
            var meanNext = mean.plus(delta.dividedBy(nNum));
            var delta2 = x.minus(meanNext);
            var m2Next = m2.plus(delta.multipliedBy(delta2));
            return new Stats(meanNext, m2Next, n);
        }

        Stats merge(Stats o, NumFactory f) {
            if (o.count == 0) {
                return this;
            }
            if (count == 0) {
                return o;
            }
            var n1 = count;
            var n2 = o.count;
            var n = n1 + n2;
            var n1Num = f.numOf(n1);
            var n2Num = f.numOf(n2);
            var nNum = f.numOf(n);
            var delta = o.mean.minus(mean);
            var meanNext = mean.plus(delta.multipliedBy(n2Num).dividedBy(nNum));
            var m2Next = m2.plus(o.m2)
                    .plus(delta.multipliedBy(delta).multipliedBy(n1Num).multipliedBy(n2Num).dividedBy(nNum));
            return new Stats(meanNext, m2Next, n);
        }

        Num sampleVariance(NumFactory f) {
            if (count < 2) {
                return f.zero();
            }
            return m2.dividedBy(f.numOf(count - 1));
        }
    }

}
