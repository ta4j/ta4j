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

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
 * of <em>excess returns</em> from the {@link CashFlow} equity curve: for each
 * sampled pair {@code (previousIndex, currentIndex)} it computes:
 * <ul>
 * <li>{@code return = equity(currentIndex) / equity(previousIndex) - 1}</li>
 * <li>{@code excessReturn = return - riskFreeReturn(previousIndex, currentIndex)}</li>
 * </ul>
 * It then returns {@code mean(excessReturn) / stdev(excessReturn)} using the
 * sample standard deviation.
 *
 * <p>
 * <b>Sampling (aggregation) of returns.</b> The {@link Sampling} parameter
 * controls how the return series is formed:
 * <ul>
 * <li>{@link Sampling#PER_BAR}: one return per bar, using consecutive bar
 * indices.</li>
 * <li>{@link Sampling#PER_SECOND}/{@link Sampling#MINUTELY}/{@link Sampling#HOURLY}/{@link Sampling#DAILY}/{@link Sampling#WEEKLY}/{@link Sampling#MONTHLY}:
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
 * annualized rate (e.g., 0.05 = 5% per year) and converted into a per-period
 * compounded return using the elapsed time between the two bar end times. If
 * {@code annualRiskFreeRate} is {@code null}, it is treated as zero.
 *
 * <p>
 * <b>Annualization.</b> When {@link Annualization#PERIOD}, the returned Sharpe
 * is per sampling period (no scaling). When {@link Annualization#ANNUALIZED},
 * the per-period Sharpe is multiplied by {@code sqrt(periodsPerYear)} where
 * {@code periodsPerYear} is estimated from observed time deltas (count of
 * positive deltas divided by the sum of deltas in years).
 *
 * @since 0.22.1
 *
 */
public class SharpeRatioCriterion extends AbstractAnalysisCriterion {

    private static final double SECONDS_PER_YEAR = 365.2425d * 24 * 3600;
    private static final WeekFields ISO_WEEK_FIELDS = WeekFields.ISO;

    public enum Sampling {
        PER_BAR, PER_SECOND, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY
    }

    public enum Annualization {
        PERIOD, ANNUALIZED
    }

    private final Num annualRiskFreeRate;
    private final Sampling sampling;
    private final Annualization annualization;
    private final ZoneId groupingZoneId;

    public SharpeRatioCriterion(Num annualRiskFreeRate, Sampling sampling, Annualization annualization,
            ZoneId groupingZoneId) {
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.sampling = sampling;
        this.annualization = annualization;
        this.groupingZoneId = groupingZoneId;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var zero = series.numFactory().zero();
        if (position == null) {
            return zero;
        }
        if (!position.isClosed()) {
            // Open positions do not have a complete return distribution (exit not fixed),
            // so Sharpe would depend on an arbitrary cutoff; returning 0 avoids misleading
            // ranking.
            return zero;
        }

        var cashFlow = new CashFlow(series, position);
        var start = Math.max(position.getEntry().getIndex() + 1, series.getBeginIndex() + 1);
        var end = Math.min(position.getExit().getIndex(), series.getEndIndex());
        var anchorIndex = position.getEntry().getIndex();

        return calculateSharpe(series, cashFlow, anchorIndex, start, end);
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

        var cashFlow = new CashFlow(series, tradingRecord);
        var start = series.getBeginIndex() + 1;
        var end = series.getEndIndex();
        var anchorIndex = series.getBeginIndex();

        return calculateSharpe(series, cashFlow, anchorIndex, start, end);
    }

    private Num calculateSharpe(BarSeries series, CashFlow cashFlow, int anchorIndex, int start, int end) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        if (end - start + 1 < 2) {
            return zero;
        }

        var pairs = indexPairs(series, anchorIndex, start, end);

        var acc = pairs.reduce(Acc.empty(zero),
                (a, p) -> a.add(excessReturn(series, cashFlow, p.previousIndex(), p.currentIndex()),
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

        var annualizationFactor = acc.annualizationFactor();
        if (annualizationFactor <= 0.0) {
            return sharpePerPeriod;
        }

        return sharpePerPeriod.multipliedBy(numFactory.numOf(annualizationFactor));
    }

    private Stream<IndexPair> indexPairs(BarSeries series, int anchorIndex, int start, int end) {
        if (sampling == Sampling.PER_BAR) {
            return IntStream.rangeClosed(start, end).mapToObj(i -> new IndexPair(i - 1, i));
        }

        var periodEndIndices = periodEndIndices(series, start, end).toArray();
        if (periodEndIndices.length == 0) {
            return Stream.empty();
        }

        var firstPair = Stream.of(new IndexPair(anchorIndex, periodEndIndices[0]));
        var consecutivePairs = IntStream.range(1, periodEndIndices.length)
                .mapToObj(k -> new IndexPair(periodEndIndices[k - 1], periodEndIndices[k]));

        return Stream.concat(firstPair, consecutivePairs);
    }

    private IntStream periodEndIndices(BarSeries series, int start, int end) {
        return IntStream.rangeClosed(start, end).filter(i -> isPeriodEnd(series, i, end));
    }

    private boolean isPeriodEnd(BarSeries series, int index, int endIndex) {
        if (index == endIndex) {
            return true;
        }

        var now = endTimeZoned(series, index);
        var next = endTimeZoned(series, index + 1);

        return switch (sampling) {
        case PER_SECOND -> !sameChronoUnit(now, next, ChronoUnit.SECONDS);
        case MINUTELY -> !sameChronoUnit(now, next, ChronoUnit.MINUTES);
        case HOURLY -> !sameChronoUnit(now, next, ChronoUnit.HOURS);
        case DAILY -> !now.toLocalDate().equals(next.toLocalDate());
        case WEEKLY -> !sameIsoWeek(now, next);
        case MONTHLY -> !YearMonth.from(now).equals(YearMonth.from(next));
        case PER_BAR -> true;
        };
    }

    private boolean sameChronoUnit(ZonedDateTime a, ZonedDateTime b, ChronoUnit chronoUnit) {
        return a.truncatedTo(chronoUnit).equals(b.truncatedTo(chronoUnit));
    }

    private boolean sameIsoWeek(ZonedDateTime a, ZonedDateTime b) {
        var weekA = a.get(ISO_WEEK_FIELDS.weekOfWeekBasedYear());
        var weekB = b.get(ISO_WEEK_FIELDS.weekOfWeekBasedYear());
        var yearA = a.get(ISO_WEEK_FIELDS.weekBasedYear());
        var yearB = b.get(ISO_WEEK_FIELDS.weekBasedYear());
        return weekA == weekB && yearA == yearB;
    }

    private ZonedDateTime endTimeZoned(BarSeries series, int index) {
        return endTimeInstant(series, index).atZone(groupingZoneId);
    }

    private Instant endTimeInstant(BarSeries series, int index) {
        return series.getBar(index).getEndTime();
    }

    private double deltaYears(BarSeries series, int previousIndex, int currentIndex) {
        var endPrev = endTimeInstant(series, previousIndex);
        var endNow = endTimeInstant(series, currentIndex);
        var seconds = Math.max(0, Duration.between(endPrev, endNow).getSeconds());
        return seconds <= 0 ? 0.0 : seconds / SECONDS_PER_YEAR;
    }

    private Num excessReturn(BarSeries series, CashFlow cashFlow, int previousIndex, int currentIndex) {
        var numFactory = series.numFactory();
        var one = numFactory.one();
        var eReturn = cashFlow.getValue(currentIndex).dividedBy(cashFlow.getValue(previousIndex)).minus(one);
        return eReturn.minus(periodRiskFree(series, previousIndex, currentIndex));
    }

    private Num periodRiskFree(BarSeries series, int previousIndex, int currentIndex) {
        var numFactory = series.numFactory();
        var deltaYears = deltaYears(series, previousIndex, currentIndex);
        if (deltaYears <= 0.0) {
            return numFactory.zero();
        }

        var annual = (annualRiskFreeRate == null) ? 0.0 : annualRiskFreeRate.doubleValue();
        var per = Math.pow(1.0 + annual, deltaYears) - 1.0;
        return numFactory.numOf(per);
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

    private record IndexPair(int previousIndex, int currentIndex) {
    }

    private record Acc(Stats stats, double deltaYearsSum, int deltaCount) {

        static Acc empty(Num zero) {
            return new Acc(Stats.empty(zero), 0.0, 0);
        }

        Acc add(Num excessReturn, double deltaYears, NumFactory numFactory) {
            var nextStats = stats.add(excessReturn, numFactory);
            if (deltaYears <= 0.0) {
                return new Acc(nextStats, deltaYearsSum, deltaCount);
            }
            return new Acc(nextStats, deltaYearsSum + deltaYears, deltaCount + 1);
        }

        Acc merge(Acc other, NumFactory numFactory) {
            var mergedStats = stats.merge(other.stats, numFactory);
            return new Acc(mergedStats, deltaYearsSum + other.deltaYearsSum, deltaCount + other.deltaCount);
        }

        double annualizationFactor() {
            if (deltaCount <= 0 || deltaYearsSum <= 0.0) {
                return 0.0;
            }
            var periodsPerYear = deltaCount / deltaYearsSum;
            return Math.sqrt(periodsPerYear);
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
