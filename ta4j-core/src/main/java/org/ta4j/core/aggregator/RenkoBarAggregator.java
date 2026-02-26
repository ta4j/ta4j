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
 * Aggregates source bars into Renko bricks.
 *
 * <p>
 * Bricks are generated from close-price movement using a configurable
 * {@code boxSize}. Reversals require a move of
 * {@code reversalAmount * boxSize}. Source bars must be contiguous and evenly
 * spaced. When multiple bricks are emitted from a single source bar, aggregated
 * volume/amount/trades are attached to the first emitted brick and set to zero
 * for additional bricks from the same source bar.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * BarAggregator renkoAggregator = new RenkoBarAggregator(2.0, 2);
 * List<Bar> renkoBars = renkoAggregator.aggregate(sourceBars);
 * }</pre>
 *
 * @since 0.22.3
 */
public class RenkoBarAggregator implements BarAggregator {

    private enum Direction {
        NONE, UP, DOWN
    }

    private final Number boxSize;
    private final int reversalAmount;

    /**
     * Creates a Renko aggregator with a two-brick reversal.
     *
     * @param boxSize the price movement represented by one brick
     * @throws NullPointerException     if {@code boxSize} is {@code null}
     * @throws IllegalArgumentException if {@code boxSize} is not a finite, positive
     *                                  value
     *
     * @since 0.22.3
     */
    public RenkoBarAggregator(Number boxSize) {
        this(boxSize, 2);
    }

    /**
     * Creates a Renko aggregator.
     *
     * @param boxSize        the price movement represented by one brick
     * @param reversalAmount the number of boxes required for reversal
     * @throws NullPointerException     if {@code boxSize} is {@code null}
     * @throws IllegalArgumentException if {@code boxSize} is not a finite, positive
     *                                  value, or if {@code reversalAmount <= 0}
     *
     * @since 0.22.3
     */
    public RenkoBarAggregator(Number boxSize, int reversalAmount) {
        this.boxSize = BarAggregator.requirePositiveFiniteNumber(boxSize, "boxSize");
        if (reversalAmount <= 0) {
            throw new IllegalArgumentException("reversalAmount must be greater than zero.");
        }
        this.reversalAmount = reversalAmount;
    }

    /**
     * Aggregates bars into Renko bricks.
     *
     * @param bars source bars in chronological order
     * @return Renko bricks
     * @throws NullPointerException     if {@code bars} is {@code null}
     * @throws IllegalArgumentException if source intervals are uneven or
     *                                  non-contiguous, or if any source bar has a
     *                                  null close price
     */
    @Override
    public List<Bar> aggregate(List<Bar> bars) {
        Objects.requireNonNull(bars, "bars");
        List<Bar> renkoBars = new ArrayList<>();
        if (bars.isEmpty()) {
            return renkoBars;
        }

        Duration sourcePeriod = requireEvenIntervals(bars);
        NumFactory numFactory = bars.getFirst().numFactory();
        Num resolvedBoxSize = numFactory.numOf(boxSize);
        Num reversalDistance = resolvedBoxSize.multipliedBy(numFactory.numOf(reversalAmount));
        Num zero = numFactory.zero();

        Num lastBrickClose = requireClosePrice(bars.getFirst(), 0);
        Direction direction = Direction.NONE;
        Num pendingVolume = zero;
        Num pendingAmount = zero;
        long pendingTrades = 0L;
        Instant nextBrickEndTime = bars.getFirst().getEndTime();

        for (int i = 0; i < bars.size(); i++) {
            Bar sourceBar = bars.get(i);
            Num closePrice = requireClosePrice(sourceBar, i);
            if (sourceBar.getVolume() != null) {
                pendingVolume = pendingVolume.plus(sourceBar.getVolume());
            }
            if (sourceBar.getAmount() != null) {
                pendingAmount = pendingAmount.plus(sourceBar.getAmount());
            }
            pendingTrades += sourceBar.getTrades();

            boolean emittedFromCurrentSourceBar = false;

            if (direction == Direction.UP || direction == Direction.NONE) {
                while (closePrice.isGreaterThanOrEqual(lastBrickClose.plus(resolvedBoxSize))) {
                    Num openPrice = lastBrickClose;
                    Num close = lastBrickClose.plus(resolvedBoxSize);
                    Instant brickEndTime = resolveBrickEndTime(sourceBar.getEndTime(), nextBrickEndTime);
                    Num brickVolume = emittedFromCurrentSourceBar ? zero : pendingVolume;
                    Num brickAmount = emittedFromCurrentSourceBar ? zero : pendingAmount;
                    long brickTrades = emittedFromCurrentSourceBar ? 0L : pendingTrades;
                    renkoBars.add(buildBrick(numFactory, sourcePeriod, brickEndTime, openPrice, close, brickVolume,
                            brickAmount, brickTrades));
                    lastBrickClose = close;
                    direction = Direction.UP;
                    nextBrickEndTime = brickEndTime.plus(sourcePeriod);
                    if (!emittedFromCurrentSourceBar) {
                        pendingVolume = zero;
                        pendingAmount = zero;
                        pendingTrades = 0L;
                        emittedFromCurrentSourceBar = true;
                    }
                }
            }

            if (direction == Direction.NONE) {
                while (closePrice.isLessThanOrEqual(lastBrickClose.minus(resolvedBoxSize))) {
                    Num openPrice = lastBrickClose;
                    Num close = lastBrickClose.minus(resolvedBoxSize);
                    Instant brickEndTime = resolveBrickEndTime(sourceBar.getEndTime(), nextBrickEndTime);
                    Num brickVolume = emittedFromCurrentSourceBar ? zero : pendingVolume;
                    Num brickAmount = emittedFromCurrentSourceBar ? zero : pendingAmount;
                    long brickTrades = emittedFromCurrentSourceBar ? 0L : pendingTrades;
                    renkoBars.add(buildBrick(numFactory, sourcePeriod, brickEndTime, openPrice, close, brickVolume,
                            brickAmount, brickTrades));
                    lastBrickClose = close;
                    direction = Direction.DOWN;
                    nextBrickEndTime = brickEndTime.plus(sourcePeriod);
                    if (!emittedFromCurrentSourceBar) {
                        pendingVolume = zero;
                        pendingAmount = zero;
                        pendingTrades = 0L;
                        emittedFromCurrentSourceBar = true;
                    }
                }
            }

            if (direction == Direction.UP && closePrice.isLessThanOrEqual(lastBrickClose.minus(reversalDistance))) {
                while (closePrice.isLessThanOrEqual(lastBrickClose.minus(resolvedBoxSize))) {
                    Num openPrice = lastBrickClose;
                    Num close = lastBrickClose.minus(resolvedBoxSize);
                    Instant brickEndTime = resolveBrickEndTime(sourceBar.getEndTime(), nextBrickEndTime);
                    Num brickVolume = emittedFromCurrentSourceBar ? zero : pendingVolume;
                    Num brickAmount = emittedFromCurrentSourceBar ? zero : pendingAmount;
                    long brickTrades = emittedFromCurrentSourceBar ? 0L : pendingTrades;
                    renkoBars.add(buildBrick(numFactory, sourcePeriod, brickEndTime, openPrice, close, brickVolume,
                            brickAmount, brickTrades));
                    lastBrickClose = close;
                    direction = Direction.DOWN;
                    nextBrickEndTime = brickEndTime.plus(sourcePeriod);
                    if (!emittedFromCurrentSourceBar) {
                        pendingVolume = zero;
                        pendingAmount = zero;
                        pendingTrades = 0L;
                        emittedFromCurrentSourceBar = true;
                    }
                }
            }

            if (direction == Direction.DOWN) {
                while (closePrice.isLessThanOrEqual(lastBrickClose.minus(resolvedBoxSize))) {
                    Num openPrice = lastBrickClose;
                    Num close = lastBrickClose.minus(resolvedBoxSize);
                    Instant brickEndTime = resolveBrickEndTime(sourceBar.getEndTime(), nextBrickEndTime);
                    Num brickVolume = emittedFromCurrentSourceBar ? zero : pendingVolume;
                    Num brickAmount = emittedFromCurrentSourceBar ? zero : pendingAmount;
                    long brickTrades = emittedFromCurrentSourceBar ? 0L : pendingTrades;
                    renkoBars.add(buildBrick(numFactory, sourcePeriod, brickEndTime, openPrice, close, brickVolume,
                            brickAmount, brickTrades));
                    lastBrickClose = close;
                    nextBrickEndTime = brickEndTime.plus(sourcePeriod);
                    if (!emittedFromCurrentSourceBar) {
                        pendingVolume = zero;
                        pendingAmount = zero;
                        pendingTrades = 0L;
                        emittedFromCurrentSourceBar = true;
                    }
                }
                if (closePrice.isGreaterThanOrEqual(lastBrickClose.plus(reversalDistance))) {
                    while (closePrice.isGreaterThanOrEqual(lastBrickClose.plus(resolvedBoxSize))) {
                        Num openPrice = lastBrickClose;
                        Num close = lastBrickClose.plus(resolvedBoxSize);
                        Instant brickEndTime = resolveBrickEndTime(sourceBar.getEndTime(), nextBrickEndTime);
                        Num brickVolume = emittedFromCurrentSourceBar ? zero : pendingVolume;
                        Num brickAmount = emittedFromCurrentSourceBar ? zero : pendingAmount;
                        long brickTrades = emittedFromCurrentSourceBar ? 0L : pendingTrades;
                        renkoBars.add(buildBrick(numFactory, sourcePeriod, brickEndTime, openPrice, close, brickVolume,
                                brickAmount, brickTrades));
                        lastBrickClose = close;
                        direction = Direction.UP;
                        nextBrickEndTime = brickEndTime.plus(sourcePeriod);
                        if (!emittedFromCurrentSourceBar) {
                            pendingVolume = zero;
                            pendingAmount = zero;
                            pendingTrades = 0L;
                            emittedFromCurrentSourceBar = true;
                        }
                    }
                }
            }
        }

        return renkoBars;
    }

    private static Num requireClosePrice(Bar bar, int index) {
        if (bar.getClosePrice() == null) {
            throw new IllegalArgumentException(String.format(
                    "RenkoBarAggregator requires close prices on all source bars. Missing at index %d.", index));
        }
        return bar.getClosePrice();
    }

    private static Bar buildBrick(NumFactory numFactory, Duration sourcePeriod, Instant endTime, Num openPrice,
            Num closePrice, Num volume, Num amount, long trades) {
        Num highPrice = openPrice.max(closePrice);
        Num lowPrice = openPrice.min(closePrice);
        return new TimeBarBuilder(numFactory).timePeriod(sourcePeriod)
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

    private static Instant resolveBrickEndTime(Instant sourceBarEndTime, Instant nextBrickEndTime) {
        return sourceBarEndTime.isAfter(nextBrickEndTime) ? sourceBarEndTime : nextBrickEndTime;
    }
}
