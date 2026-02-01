/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * An {@link Indicator} implementation that computes the cumulative profit and
 * loss (PnL) series of one or more trading positions over a given
 * {@link BarSeries}.
 * <p>
 * The cumulative PnL is calculated incrementally from the start of the
 * {@code BarSeries}, taking into account realized and unrealized gains/losses,
 * trading costs, and position direction (long or short). Each index in the
 * series represents the total PnL up to that bar.
 * </p>
 *
 * @since 0.19
 */
public final class CumulativePnL implements Indicator<Num> {

    private final BarSeries barSeries;
    private final List<Num> values;

    /**
     * Constructor for a single closed position.
     *
     * @param barSeries the bar series
     * @param position  the closed position
     *
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, Position position) {
        if (position.isOpened()) {
            throw new IllegalArgumentException("Position is not closed. Provide a final index if open.");
        }
        this.barSeries = barSeries;
        this.values = new ArrayList<>(Collections.singletonList(barSeries.numFactory().zero()));
        calculate(position, position.getExit().getIndex());
        fillToTheEnd(barSeries.getEndIndex());
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     *
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries));
    }

    /**
     * Constructor for a trading record with a specified final index.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param finalIndex    the final index to calculate up to
     *
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this.barSeries = barSeries;
        this.values = new ArrayList<>(Collections.singletonList(barSeries.numFactory().zero()));

        var positions = tradingRecord.getPositions();
        for (var position : positions) {
            var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
            calculate(position, endIndex);
        }
        if (tradingRecord.getCurrentPosition().isOpened()) {
            calculate(tradingRecord.getCurrentPosition(), finalIndex);
        }
        fillToTheEnd(finalIndex);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public BarSeries getBarSeries() {
        return barSeries;
    }

    /**
     * Returns the number of bars in the underlying series.
     *
     * @return the bar count
     *
     * @since 0.19
     */
    public int getSize() {
        return barSeries.getBarCount();
    }

    private void calculate(Position position, int finalIndex) {
        var numFactory = barSeries.numFactory();
        var zero = numFactory.zero();
        var isLong = position.getEntry().isBuy();
        var entryIndex = position.getEntry().getIndex();
        var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        var begin = entryIndex + 1;

        if (begin > values.size()) {
            var last = values.getLast();
            values.addAll(Collections.nCopies(begin - values.size(), last));
        }

        var periods = Math.max(0, endIndex - entryIndex);
        var holdingCost = position.getHoldingCost(endIndex);
        var averageCostPerPeriod = periods > 0 ? holdingCost.dividedBy(numFactory.numOf(periods)) : zero;
        var netEntryPrice = position.getEntry().getNetPrice();
        var baseAtEntry = values.get(entryIndex);
        var startingIndex = Math.max(begin, 1);

        for (var i = startingIndex; i < endIndex; i++) {
            var close = barSeries.getBar(i).getClosePrice();
            var netIntermediate = AnalysisUtils.addCost(close, averageCostPerPeriod, isLong);
            var delta = isLong ? netIntermediate.minus(netEntryPrice) : netEntryPrice.minus(netIntermediate);
            values.add(baseAtEntry.plus(delta));
        }

        var exitRaw = Objects.nonNull(position.getExit()) ? position.getExit().getNetPrice()
                : barSeries.getBar(endIndex).getClosePrice();
        var netExit = AnalysisUtils.addCost(exitRaw, averageCostPerPeriod, isLong);
        var deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
        values.add(baseAtEntry.plus(deltaExit));
    }

    private void fillToTheEnd(int endIndex) {
        if (endIndex >= values.size()) {
            var last = values.getLast();
            values.addAll(Collections.nCopies(barSeries.getEndIndex() - values.size() + 1, last));
        }
    }

}
