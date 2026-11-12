/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Funding settlements on base-asset amounts at historical mark prices. Positive
 * rates charge longs and credit shorts; negative rates reverse that direction.
 * Positive costs are paid and negative costs are received.
 *
 * <p>
 * Each bar in {@code (entryIndex, min(exitIndex, finalIndex)]} contributes
 * {@code rate * mark * entryAmount}, with the sign reversed for shorts. Reads
 * are restricted to the indicators' retained series bounds. A NaN rate means no
 * settlement; a zero rate does not read the mark. No results are cached, so
 * historical corrections and moving series are reflected on the next call. The
 * retained bounds and published history revision are checked after indicator
 * evaluation, outside all series read scopes. A concurrent eviction or
 * correction fails explicitly instead of returning settlements from mixed
 * histories.
 *
 * @since 0.28.1
 */
public final class FundingCostModel implements CostModel {

    private final Indicator<Num> fundingRate;
    private final Indicator<Num> markPrice;

    /**
     * Uses the funding series' close price as the mark price.
     *
     * @param fundingRate settlement rates, not annualized rates
     * @throws NullPointerException if the indicator or its series is null
     * @since 0.28.1
     */
    public FundingCostModel(Indicator<Num> fundingRate) {
        this(fundingRate, new ClosePriceIndicator(Objects.requireNonNull(fundingRate, "fundingRate").getBarSeries()));
    }

    /**
     * Uses explicit historical settlement rates and mark prices.
     *
     * @param fundingRate settlement rates; NaN denotes no settlement
     * @param markPrice   mark prices at the same absolute indices
     * @throws NullPointerException     if either indicator or its series is null
     * @throws IllegalArgumentException if the series use incompatible numeric types
     * @since 0.28.1
     */
    public FundingCostModel(Indicator<Num> fundingRate, Indicator<Num> markPrice) {
        this.fundingRate = Objects.requireNonNull(fundingRate, "fundingRate");
        this.markPrice = Objects.requireNonNull(markPrice, "markPrice");
        Objects.requireNonNull(fundingRate.getBarSeries(), "fundingRate series");
        Objects.requireNonNull(markPrice.getBarSeries(), "markPrice series");
        if (!fundingRate.getBarSeries().numFactory().produces(markPrice.getBarSeries().numFactory().one())) {
            throw new IllegalArgumentException("Funding rates and mark prices must use compatible numeric types");
        }
    }

    /**
     * Creates UTC epoch-aligned fixed-rate funding, using close prices as marks. A
     * bar's rate is multiplied by the number of interval boundaries in
     * {@code (previousBarEnd, barEnd]}; the first retained bar uses its own begin
     * time. Gaps and bars spanning multiple settlements are counted in full.
     *
     * @param series          the price and scheduling series
     * @param ratePerInterval finite rate per settlement, possibly negative
     * @param interval        positive settlement interval (for example eight hours)
     * @return the holding cost model
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the rate is non-finite or the interval is
     *                                  not positive
     * @since 0.28.1
     */
    public static FundingCostModel fixedRate(BarSeries series, Number ratePerInterval, Duration interval) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(ratePerInterval, "ratePerInterval");
        Objects.requireNonNull(interval, "interval");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Interval must be positive");
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(ratePerInterval.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Rate must be finite", exception);
        }
        return new FundingCostModel(new ScheduledRateIndicator(series, rate, interval));
    }

    /**
     * Returns zero: a price and amount alone contain no holding window.
     *
     * @since 0.28.1
     */
    @Override
    public Num calculate(Num price, Num amount) {
        Objects.requireNonNull(amount, "amount");
        return Objects.requireNonNull(price, "price").getNumFactory().zero();
    }

    /**
     * Calculates the complete funding cost of a closed position.
     *
     * @throws IllegalArgumentException if the position is open
     * @throws NullPointerException     if the position is null
     * @since 0.28.1
     */
    @Override
    public Num calculate(Position position) {
        Objects.requireNonNull(position, "position");
        if (position.isOpened()) {
            throw new IllegalArgumentException(
                    "Position is not closed. Final index of observation needs to be provided.");
        }
        return calculate(position, position.isClosed() ? position.getExit().getIndex() : -1);
    }

    /**
     * Calculates funding through the supplied index, capped at the actual exit.
     * Empty positions and empty windows have zero cost.
     *
     * @throws NullPointerException            if the position is null
     * @throws ConcurrentModificationException if retained input history changes
     *                                         during indicator evaluation
     * @since 0.28.1
     */
    @Override
    public Num calculate(Position position, int finalIndex) {
        Objects.requireNonNull(position, "position");
        BarSeries rates = fundingRate.getBarSeries();
        BarSeries marks = markPrice.getBarSeries();
        Num total = rates.numFactory().zero();
        Trade entry = position.getEntry();
        if (entry == null) {
            return total;
        }
        InputWindow rateWindow = InputWindow.capture(rates);
        InputWindow markWindow = marks == rates ? rateWindow : InputWindow.capture(marks);
        long start = Math.max((long) entry.getIndex() + 1, Math.max(rateWindow.beginIndex(), markWindow.beginIndex()));
        int end = Math.min(finalIndex, Math.min(rateWindow.endIndex(), markWindow.endIndex()));
        if (position.isClosed()) {
            end = Math.min(end, position.getExit().getIndex());
        }
        for (long index = start; index <= end; index++) {
            Num rate = fundingRate.getValue((int) index);
            if (!rate.isNaN() && !rate.isZero()) {
                total = total.plus(rate.multipliedBy(markPrice.getValue((int) index)));
            }
        }
        if (start <= end) {
            rateWindow.validate(rates);
            if (marks != rates) {
                markWindow.validate(marks);
            }
        }
        Num cost = total.multipliedBy(entry.getAmount());
        return entry.isBuy() ? cost : cost.negate();
    }

    /**
     * Compares the indicator instances, not their current values.
     *
     * @since 0.28.1
     */
    @Override
    public boolean equals(CostModel otherModel) {
        return otherModel instanceof FundingCostModel other && fundingRate == other.fundingRate
                && markPrice == other.markPrice;
    }

    private record InputWindow(int beginIndex, int endIndex, long revision) {

        private static InputWindow capture(BarSeries series) {
            return series.withReadLock(() -> new InputWindow(series.getBeginIndex(), series.getEndIndex(),
                    series.getBarHistoryRevision()));
        }

        private void validate(BarSeries series) {
            series.withReadLock(() -> {
                if (series.getBeginIndex() != beginIndex || series.getEndIndex() < endIndex
                        || series.getBarHistoryRevision() != revision) {
                    throw new ConcurrentModificationException("Funding input history changed during evaluation");
                }
            });
        }
    }

    private static final class ScheduledRateIndicator extends AbstractIndicator<Num> {
        private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000);
        private final Num rate;
        private final Duration interval;
        private final BigInteger intervalNanos;

        private ScheduledRateIndicator(BarSeries series, BigDecimal rate, Duration interval) {
            super(series);
            this.rate = series.numFactory().numOf(rate);
            this.interval = interval;
            this.intervalNanos = BigInteger.valueOf(interval.getSeconds())
                    .multiply(NANOS_PER_SECOND)
                    .add(BigInteger.valueOf(interval.getNano()));
        }

        @Override
        public Num getValue(int index) {
            BarSeries series = getBarSeries();
            Bar bar = series.getBar(index);
            Instant start = index > series.getBeginIndex() ? series.getBar(index - 1).getEndTime() : bar.getBeginTime();
            Instant end = bar.getEndTime();
            if (interval.getNano() == 0) {
                long count = Math.floorDiv(end.getEpochSecond(), interval.getSeconds())
                        - Math.floorDiv(start.getEpochSecond(), interval.getSeconds());
                return rate.multipliedBy(series.numFactory().numOf(count));
            }
            // BigInteger preserves nanosecond alignment without epoch/count overflow.
            BigInteger count = settlementNumber(end).subtract(settlementNumber(start));
            return rate.multipliedBy(series.numFactory().numOf(count));
        }

        private BigInteger settlementNumber(Instant instant) {
            BigInteger nanos = BigInteger.valueOf(instant.getEpochSecond())
                    .multiply(NANOS_PER_SECOND)
                    .add(BigInteger.valueOf(instant.getNano()));
            BigInteger[] division = nanos.divideAndRemainder(intervalNanos);
            return nanos.signum() < 0 && division[1].signum() != 0 ? division[0].subtract(BigInteger.ONE) : division[0];
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
