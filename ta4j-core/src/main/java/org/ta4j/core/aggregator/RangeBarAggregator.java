/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Aggregates source bars into range bars.
 *
 * <p>
 * A range bar is closed once the aggregated high-low range reaches the
 * configured range size. Source bars must be contiguous and evenly spaced.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * BarAggregator rangeAggregator = new RangeBarAggregator(2.5);
 * List<Bar> rangeBars = rangeAggregator.aggregate(sourceBars);
 * }</pre>
 *
 * <p>
 * Source bars that do not complete the configured range are emitted only when
 * {@code onlyFinalBars} is {@code false}.
 *
 * @since 0.22.3
 */
public class RangeBarAggregator implements BarAggregator {

    private final Number rangeSize;
    private final boolean onlyFinalBars;

    /**
     * Creates a range-bar aggregator that emits only completed range bars.
     *
     * @param rangeSize the minimum high-low range required to close a bar
     * @throws NullPointerException     if {@code rangeSize} is {@code null}
     * @throws IllegalArgumentException if {@code rangeSize} is not a finite,
     *                                  positive value
     *
     * @since 0.22.3
     */
    public RangeBarAggregator(Number rangeSize) {
        this(rangeSize, true);
    }

    /**
     * Creates a range-bar aggregator.
     *
     * @param rangeSize     the minimum high-low range required to close a bar
     * @param onlyFinalBars if {@code true}, incomplete trailing bars are omitted
     * @throws NullPointerException     if {@code rangeSize} is {@code null}
     * @throws IllegalArgumentException if {@code rangeSize} is not a finite,
     *                                  positive value
     *
     * @since 0.22.3
     */
    public RangeBarAggregator(Number rangeSize, boolean onlyFinalBars) {
        this.rangeSize = SourceIntervalValidator.requirePositiveFiniteNumber(rangeSize, "rangeSize");
        this.onlyFinalBars = onlyFinalBars;
    }

    /**
     * Aggregates bars into range bars.
     *
     * @param bars source bars in chronological order
     * @return range bars
     * @throws NullPointerException     if {@code bars} is {@code null}
     * @throws IllegalArgumentException if source intervals are uneven or
     *                                  non-contiguous
     */
    @Override
    public List<Bar> aggregate(List<Bar> bars) {
        Objects.requireNonNull(bars, "bars");
        List<Bar> aggregated = new ArrayList<>();
        if (bars.isEmpty()) {
            return aggregated;
        }

        SourceIntervalValidator.requireEvenIntervals(bars, getClass().getSimpleName());

        NumFactory numFactory = bars.getFirst().numFactory();
        Num resolvedRangeSize = numFactory.numOf(rangeSize);
        Num zero = numFactory.zero();

        Instant currentBeginTime = null;
        Instant currentEndTime = null;
        Num currentOpenPrice = null;
        Num currentHighPrice = null;
        Num currentLowPrice = null;
        Num currentClosePrice = null;
        Num currentVolume = zero;
        Num currentAmount = zero;
        long currentTrades = 0L;

        for (Bar bar : bars) {
            if (currentBeginTime == null) {
                currentBeginTime = bar.getBeginTime();
                currentOpenPrice = bar.getOpenPrice();
                currentHighPrice = bar.getHighPrice();
                currentLowPrice = bar.getLowPrice();
            } else {
                if (currentHighPrice == null
                        || (bar.getHighPrice() != null && bar.getHighPrice().isGreaterThan(currentHighPrice))) {
                    currentHighPrice = bar.getHighPrice();
                }
                if (currentLowPrice == null
                        || (bar.getLowPrice() != null && bar.getLowPrice().isLessThan(currentLowPrice))) {
                    currentLowPrice = bar.getLowPrice();
                }
            }

            currentEndTime = bar.getEndTime();
            currentClosePrice = bar.getClosePrice();
            if (bar.getVolume() != null) {
                currentVolume = currentVolume.plus(bar.getVolume());
            }
            if (bar.getAmount() != null) {
                currentAmount = currentAmount.plus(bar.getAmount());
            }
            currentTrades += bar.getTrades();

            Num currentRange = zero;
            if (currentHighPrice != null && currentLowPrice != null) {
                currentRange = currentHighPrice.minus(currentLowPrice);
            }
            if (currentRange.isGreaterThanOrEqual(resolvedRangeSize)) {
                aggregated.add(buildAggregatedBar(numFactory, currentBeginTime, currentEndTime, currentOpenPrice,
                        currentHighPrice, currentLowPrice, currentClosePrice, currentVolume, currentAmount,
                        currentTrades));

                currentBeginTime = null;
                currentEndTime = null;
                currentOpenPrice = null;
                currentHighPrice = null;
                currentLowPrice = null;
                currentClosePrice = null;
                currentVolume = zero;
                currentAmount = zero;
                currentTrades = 0L;
            }
        }

        if (!onlyFinalBars && currentBeginTime != null) {
            aggregated.add(buildAggregatedBar(numFactory, currentBeginTime, currentEndTime, currentOpenPrice,
                    currentHighPrice, currentLowPrice, currentClosePrice, currentVolume, currentAmount, currentTrades));
        }

        return aggregated;
    }

    private static Bar buildAggregatedBar(NumFactory numFactory, Instant beginTime, Instant endTime, Num openPrice,
            Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount, long trades) {
        Duration aggregatedPeriod = Duration.between(beginTime, endTime);
        return new TimeBarBuilder(numFactory).timePeriod(aggregatedPeriod)
                .endTime(endTime)
                .openPrice(openPrice)
                .highPrice(highPrice)
                .lowPrice(lowPrice)
                .closePrice(closePrice)
                .volume(volume)
                .amount(amount)
                .trades(trades)
                .build();
    }
}
