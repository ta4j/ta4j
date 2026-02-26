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
 * Aggregates source bars into volume-threshold bars.
 *
 * <p>
 * A volume bar is closed once the aggregated volume reaches the configured
 * threshold. Source bars must be contiguous and evenly spaced.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * BarAggregator volumeAggregator = new VolumeBarAggregator(10_000);
 * List<Bar> volumeBars = volumeAggregator.aggregate(sourceBars);
 * }</pre>
 *
 * <p>
 * Source bars that do not complete the configured volume threshold are emitted
 * only when {@code onlyFinalBars} is {@code false}.
 *
 * @since 0.22.3
 */
public class VolumeBarAggregator implements BarAggregator {

    private final Number volumeThreshold;
    private final boolean onlyFinalBars;

    /**
     * Creates a volume-bar aggregator that emits only completed volume bars.
     *
     * @param volumeThreshold the minimum aggregated volume required to close a bar
     * @throws NullPointerException     if {@code volumeThreshold} is {@code null}
     * @throws IllegalArgumentException if {@code volumeThreshold} is not a finite,
     *                                  positive value
     *
     * @since 0.22.3
     */
    public VolumeBarAggregator(Number volumeThreshold) {
        this(volumeThreshold, true);
    }

    /**
     * Creates a volume-bar aggregator.
     *
     * @param volumeThreshold the minimum aggregated volume required to close a bar
     * @param onlyFinalBars   if {@code true}, incomplete trailing bars are omitted
     * @throws NullPointerException     if {@code volumeThreshold} is {@code null}
     * @throws IllegalArgumentException if {@code volumeThreshold} is not a finite,
     *                                  positive value
     *
     * @since 0.22.3
     */
    public VolumeBarAggregator(Number volumeThreshold, boolean onlyFinalBars) {
        this.volumeThreshold = SourceIntervalValidator.requirePositiveFiniteNumber(volumeThreshold, "volumeThreshold");
        this.onlyFinalBars = onlyFinalBars;
    }

    /**
     * Aggregates bars into volume bars.
     *
     * @param bars source bars in chronological order
     * @return volume bars
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
        Num resolvedVolumeThreshold = numFactory.numOf(volumeThreshold);
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

            if (currentVolume.isGreaterThanOrEqual(resolvedVolumeThreshold)) {
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
