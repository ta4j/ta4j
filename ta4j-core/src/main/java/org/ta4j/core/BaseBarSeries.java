/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base implementation of a {@link BarSeries}.
 */
public class BaseBarSeries implements BarSeries {

    @Serial
    private static final long serialVersionUID = -1878027009398790126L;

    /**
     * The logger.
     */
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The name of the bar series.
     */
    private final String name;

    /**
     * The list of bars of the bar series.
     */
    private final List<Bar> bars;
    private final BarBuilderFactory barBuilderFactory;

    private final NumFactory numFactory;
    /**
     * True if the current bar series is constrained (i.e. its indexes cannot
     * change), false otherwise.
     */
    private final boolean constrained;
    /**
     * The begin index of the bar series
     */
    private int seriesBeginIndex = -1;
    /**
     * The end index of the bar series.
     */
    private int seriesEndIndex = -1;
    /**
     * The maximum number of bars for the bar series.
     */
    private int maximumBarCount = Integer.MAX_VALUE;
    /**
     * The number of removed bars.
     */
    private int removedBarsCount = 0;

    /**
     * Convenience constructor for BaseBarSeries minimizing upfront parameter
     * setting. Defaults to Time-based bars, and DecimalNum values
     *
     * @param name the name of the bar series
     * @param bars the list of bars of the bar series
     */
    public BaseBarSeries(final String name, final List<Bar> bars) {
        this(name, bars, 0, bars.size() - 1, false, DecimalNumFactory.getInstance(), new TimeBarBuilderFactory());
    }

    /**
     * Constructor.
     *
     * @param name              the name of the bar series
     * @param bars              the list of bars of the bar series
     * @param seriesBeginIndex  the begin index (inclusive) of the bar series
     * @param seriesEndIndex    the end index (inclusive) of the bar series
     * @param constrained       true to constrain the bar series (i.e. indexes
     *                          cannot change), false otherwise
     * @param numFactory        the factory of numbers used in series {@link Num Num
     *                          implementation}
     * @param barBuilderFactory factory for creating bars of this series
     */
    BaseBarSeries(final String name, final List<Bar> bars, final int seriesBeginIndex, final int seriesEndIndex,
            final boolean constrained, final NumFactory numFactory, final BarBuilderFactory barBuilderFactory) {
        this.name = name;
        this.numFactory = numFactory;

        this.bars = new ArrayList<>(bars);
        this.barBuilderFactory = Objects.requireNonNull(barBuilderFactory);
        if (bars.isEmpty()) {
            // Bar list empty
            this.constrained = false;
            return;
        }
        // Bar list not empty: checking indexes
        if (seriesEndIndex < seriesBeginIndex - 1) {
            throw new IllegalArgumentException("End index must be >= to begin index - 1");
        }
        if (seriesEndIndex >= bars.size()) {
            throw new IllegalArgumentException("End index must be < to the bar list size");
        }
        this.seriesBeginIndex = seriesBeginIndex;
        this.seriesEndIndex = seriesEndIndex;
        this.constrained = constrained;
    }

    /**
     * Cuts a list of bars into a new list of bars that is a subset of it.
     *
     * @param bars       the list of {@link Bar bars}
     * @param startIndex start index of the subset
     * @param endIndex   end index of the subset
     * @return a new list of bars with tick from startIndex (inclusive) to endIndex
     *         (exclusive)
     */
    private static List<Bar> cut(final List<Bar> bars, final int startIndex, final int endIndex) {
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    /**
     * @param series a bar series
     * @param index  an out-of-bounds bar index
     * @return a message for an OutOfBoundsException
     */
    private static String buildOutOfBoundsMessage(final BaseBarSeries series, final int index) {
        return String.format("Size of series: %s bars, %s bars removed, index = %s", series.bars.size(),
                series.removedBarsCount, index);
    }

    @Override
    public BaseBarSeries getSubSeries(final int startIndex, final int endIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException(String.format("the startIndex: %s must not be negative", startIndex));
        }
        if (startIndex >= endIndex) {
            throw new IllegalArgumentException(
                    String.format("the endIndex: %s must be greater than startIndex: %s", endIndex, startIndex));
        }
        var builder = new BaseBarSeriesBuilder().withName(getName())
                .withNumFactory(this.numFactory)
                .withMaxBarCount(this.maximumBarCount);
        if (!this.bars.isEmpty()) {
            var removedBarsCount = getRemovedBarsCount();
            var start = startIndex - removedBarsCount;
            var end = Math.min(endIndex - removedBarsCount, this.getEndIndex() + 1);
            return builder.withBars(cut(this.bars, start, end)).build();
        }
        return builder.build();
    }

    @Override
    public NumFactory numFactory() {
        return this.numFactory;
    }

    @Override
    public BarBuilder barBuilder() {
        return barBuilderFactory.createBarBuilder(this);
    }

    protected BarBuilderFactory barBuilderFactory() {
        return barBuilderFactory;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Bar getBar(final int i) {
        int innerIndex = i - this.removedBarsCount;
        if (innerIndex < 0) {
            if (i < 0) {
                // Cannot return the i-th bar if i < 0
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
            }
            if (this.log.isTraceEnabled()) {
                this.log.trace("Bar series `{}` ({} bars): bar {} already removed, use {}-th instead", this.name,
                        this.bars.size(), i, this.removedBarsCount);
            }
            if (this.bars.isEmpty()) {
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, this.removedBarsCount));
            }
            innerIndex = 0;
        } else if (innerIndex >= this.bars.size()) {
            // Cannot return the n-th bar if n >= bars.size()
            throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
        }
        return this.bars.get(innerIndex);
    }

    @Override
    public int getBarCount() {
        if (this.seriesEndIndex < 0) {
            return 0;
        }
        final int startIndex = Math.max(this.removedBarsCount, this.seriesBeginIndex);
        return this.seriesEndIndex - startIndex + 1;
    }

    @Override
    public List<Bar> getBarData() {
        return this.bars;
    }

    @Override
    public int getBeginIndex() {
        return this.seriesBeginIndex;
    }

    @Override
    public int getEndIndex() {
        return this.seriesEndIndex;
    }

    @Override
    public int getMaximumBarCount() {
        return this.maximumBarCount;
    }

    boolean isConstrained() {
        return this.constrained;
    }

    @Override
    public void setMaximumBarCount(final int maximumBarCount) {
        if (this.constrained) {
            throw new IllegalStateException("Cannot set a maximum bar count on a constrained bar series");
        }
        if (maximumBarCount <= 0) {
            throw new IllegalArgumentException("Maximum bar count must be strictly positive");
        }
        this.maximumBarCount = maximumBarCount;
        removeExceedingBars();
    }

    @Override
    public int getRemovedBarsCount() {
        return this.removedBarsCount;
    }

    /**
     * @throws NullPointerException if {@code bar} is {@code null}
     */
    @Override
    public void addBar(final Bar bar, final boolean replace) {
        Objects.requireNonNull(bar, "bar must not be null");
        if (!numFactory.produces(bar.getClosePrice())) {
            throw new IllegalArgumentException(
                    String.format("Cannot add Bar with data type: %s to series with datatype: %s",
                            bar.getClosePrice().getClass(), this.numFactory.one().getClass()));
        }

        if (!this.bars.isEmpty()) {
            if (replace) {
                this.bars.set(this.bars.size() - 1, bar);
                return;
            }
            final int lastBarIndex = this.bars.size() - 1;
            final Instant seriesEndTime = this.bars.get(lastBarIndex).getEndTime();
            if (!bar.getEndTime().isAfter(seriesEndTime)) {
                throw new IllegalArgumentException(
                        String.format("Cannot add a bar with end time:%s that is <= to series end time: %s",
                                bar.getEndTime(), seriesEndTime));
            }
        }

        this.bars.add(bar);
        if (this.seriesBeginIndex == -1) {
            // The begin index is set to 0 if not already initialized:
            this.seriesBeginIndex = 0;
        }
        this.seriesEndIndex++;
        removeExceedingBars();
    }

    /**
     * Replaces a bar at the provided series index without changing bar count or
     * indices.
     *
     * @param index the series index to replace
     * @param bar   the replacement bar
     *
     * @throws NullPointerException      if {@code bar} is {@code null}
     * @throws IllegalArgumentException  if the bar does not match the series
     *                                   numFactory
     * @throws IndexOutOfBoundsException if the index is outside the current series
     *                                   window
     */
    protected void replaceBar(final int index, final Bar bar) {
        Objects.requireNonNull(bar, "bar must not be null");
        if (!numFactory.produces(bar.getClosePrice())) {
            throw new IllegalArgumentException(
                    String.format("Cannot add Bar with data type: %s to series with datatype: %s",
                            bar.getClosePrice().getClass(), this.numFactory.one().getClass()));
        }
        if (index < this.seriesBeginIndex || index > this.seriesEndIndex || this.bars.isEmpty()) {
            throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, index));
        }
        final int innerIndex = index - this.removedBarsCount;
        if (innerIndex < 0 || innerIndex >= this.bars.size()) {
            throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, index));
        }
        this.bars.set(innerIndex, bar);
    }

    @Override
    public void addTrade(final Number tradeVolume, final Number tradePrice) {
        addTrade(numFactory().numOf(tradeVolume), numFactory().numOf(tradePrice));
    }

    @Override
    public void addTrade(final Num tradeVolume, final Num tradePrice) {
        getLastBar().addTrade(tradeVolume, tradePrice);
    }

    @Override
    public void addPrice(final Num price) {
        getLastBar().addPrice(price);
    }

    /**
     * Removes the first N bars that exceed the {@link #maximumBarCount}.
     */
    protected void removeExceedingBars() {
        final int barCount = this.bars.size();
        if (barCount > this.maximumBarCount) {
            // Removing old bars
            final int nbBarsToRemove = barCount - this.maximumBarCount;
            if (nbBarsToRemove == 1) {
                this.bars.removeFirst();
            } else {
                this.bars.subList(0, nbBarsToRemove).clear();
            }
            // Updating removed bars count
            this.removedBarsCount += nbBarsToRemove;
            this.seriesBeginIndex = Math.max(this.seriesBeginIndex, this.removedBarsCount);
        }
    }

}
