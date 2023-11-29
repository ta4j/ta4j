/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core;

import static org.ta4j.core.num.NaN.NaN;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link BarSeries}.
 */
public class BaseBarSeries implements BarSeries {

    private static final long serialVersionUID = -1878027009398790126L;

    /** The logger. */
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    /** The {@link #name} for an unnamed bar series. */
    private static final String UNNAMED_SERIES_NAME = "unnamed_series";

    /** Any instance of Num to determine its Num type. */
    protected final transient Num num;

    /** The name of the bar series. */
    private final String name;

    /** The list of bars of the bar series. */
    private final List<Bar> bars;

    /** The begin index of the bar series */
    private int seriesBeginIndex;

    /** The end index of the bar series. */
    private int seriesEndIndex;

    /** The maximum number of bars for the bar series. */
    private int maximumBarCount = Integer.MAX_VALUE;

    /** The number of removed bars. */
    private int removedBarsCount = 0;

    /**
     * True if the current bar series is constrained (i.e. its indexes cannot
     * change), false otherwise.
     */
    private final boolean constrained;

    /** Constructor with {@link #name} = {@link #UNNAMED_SERIES_NAME}. */
    public BaseBarSeries() {
        this(UNNAMED_SERIES_NAME);
    }

    /**
     * Constructor.
     *
     * @param name the name of the bar series
     */
    public BaseBarSeries(String name) {
        this(name, new ArrayList<>());
    }

    /**
     * Constructor with {@link #name} = {@link #UNNAMED_SERIES_NAME}.
     *
     * @param bars the list of bars of the bar series
     */
    public BaseBarSeries(List<Bar> bars) {
        this(UNNAMED_SERIES_NAME, bars);
    }

    /**
     * Constructor.
     *
     * @param name the name of the bar series
     * @param bars the list of bars of the bar series
     */
    public BaseBarSeries(String name, List<Bar> bars) {
        this(name, bars, 0, bars.size() - 1, false);
    }

    /**
     * Constructor.
     *
     * @param name the name of the bar series
     * @param num  any instance of Num to determine its Num function; with this, we
     *             can convert a {@link Number} to a {@link Num Num implementation}
     */
    public BaseBarSeries(String name, Num num) {
        this(name, new ArrayList<>(), num);
    }

    /**
     * Constructor.
     *
     * @param name the name of the bar series
     * @param bars the list of bars of the bar series
     * @param num  any instance of Num to determine its Num function; with this, we
     *             can convert a {@link Number} to a {@link Num Num implementation}
     */
    public BaseBarSeries(String name, List<Bar> bars, Num num) {
        this(name, bars, 0, bars.size() - 1, false, num);
    }

    /**
     * Constructor.
     * <p/>
     * Creates a BaseBarSeries with default {@link DecimalNum} as type for the data
     * and all operations on it
     *
     * @param name             the name of the bar series
     * @param bars             the list of bars of the bar series
     * @param seriesBeginIndex the begin index (inclusive) of the bar series
     * @param seriesEndIndex   the end index (inclusive) of the bar series
     * @param constrained      true to constrain the bar series (i.e. indexes cannot
     *                         change), false otherwise
     */
    private BaseBarSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained) {
        this(name, bars, seriesBeginIndex, seriesEndIndex, constrained, DecimalNum.ZERO);
    }

    /**
     * Constructor.
     *
     * @param name             the name of the bar series
     * @param bars             the list of bars of the bar series
     * @param seriesBeginIndex the begin index (inclusive) of the bar series
     * @param seriesEndIndex   the end index (inclusive) of the bar series
     * @param constrained      true to constrain the bar series (i.e. indexes cannot
     *                         change), false otherwise
     * @param num              any instance of Num to determine its Num function;
     *                         with this, we can convert a {@link Number} to a
     *                         {@link Num Num implementation}
     */
    BaseBarSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained, Num num) {
        this.name = name;

        this.bars = new ArrayList<>(bars);
        if (bars.isEmpty()) {
            // Bar list empty
            this.seriesBeginIndex = -1;
            this.seriesEndIndex = -1;
            this.constrained = false;
            this.num = num;
            return;
        }
        // Bar list not empty: take Function of first bar
        this.num = bars.get(0).getClosePrice();
        // Bar list not empty: checking num types
        if (!checkBars(bars)) {
            throw new IllegalArgumentException(String.format(
                    "Num implementation of bars: %s" + " does not match to Num implementation of bar series: %s",
                    bars.get(0).getClosePrice().getClass(), num.function()));
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
    private static List<Bar> cut(List<Bar> bars, final int startIndex, final int endIndex) {
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    /**
     * @param series a bar series
     * @param index  an out of bounds bar index
     * @return a message for an OutOfBoundsException
     */
    private static String buildOutOfBoundsMessage(BaseBarSeries series, int index) {
        return String.format("Size of series: %s bars, %s bars removed, index = %s", series.bars.size(),
                series.removedBarsCount, index);
    }

    @Override
    public BaseBarSeries getSubSeries(int startIndex, int endIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException(String.format("the startIndex: %s must not be negative", startIndex));
        }
        if (startIndex >= endIndex) {
            throw new IllegalArgumentException(
                    String.format("the endIndex: %s must be greater than startIndex: %s", endIndex, startIndex));
        }
        if (!bars.isEmpty()) {
            int start = startIndex - getRemovedBarsCount();
            int end = Math.min(endIndex - getRemovedBarsCount(), this.getEndIndex() + 1);
            return new BaseBarSeries(getName(), cut(bars, start, end), num);
        }
        return new BaseBarSeries(name, num);

    }

    @Override
    public Num num() {
        return num;
    }

    /**
     * Checks if all {@link Bar bars} of a list fits to the {@link Num NumFunction}
     * used by this bar series.
     *
     * @param bars a List of Bar objects.
     * @return false if a Num implementation of at least one Bar does not fit.
     */
    private boolean checkBars(List<Bar> bars) {
        for (Bar bar : bars) {
            if (!checkBar(bar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the {@link Num} implementation of a {@link Bar} fits to the
     * NumFunction used by this bar series.
     *
     * @param bar a Bar object.
     * @return false if another Num implementation is used than by this bar series.
     * @see Num
     * @see Bar
     * @see #addBar(Duration, ZonedDateTime)
     */
    private boolean checkBar(Bar bar) {
        if (bar.getClosePrice() == null) {
            return true; // bar has not been initialized with data (uses deprecated constructor)
        }
        // all other constructors initialize at least the close price, check if Num
        // implementation fits to numFunction
        Class<? extends Num> f = one().getClass();
        return f == bar.getClosePrice().getClass() || bar.getClosePrice().equals(NaN);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Bar getBar(int i) {
        int innerIndex = i - removedBarsCount;
        if (innerIndex < 0) {
            if (i < 0) {
                // Cannot return the i-th bar if i < 0
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
            }
            if (log.isTraceEnabled()) {
                log.trace("Bar series `{}` ({} bars): bar {} already removed, use {}-th instead", name, bars.size(), i,
                        removedBarsCount);
            }
            if (bars.isEmpty()) {
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, removedBarsCount));
            }
            innerIndex = 0;
        } else if (innerIndex >= bars.size()) {
            // Cannot return the n-th bar if n >= bars.size()
            throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
        }
        return bars.get(innerIndex);
    }

    @Override
    public int getBarCount() {
        if (seriesEndIndex < 0) {
            return 0;
        }
        final int startIndex = Math.max(removedBarsCount, seriesBeginIndex);
        return seriesEndIndex - startIndex + 1;
    }

    @Override
    public List<Bar> getBarData() {
        return bars;
    }

    @Override
    public int getBeginIndex() {
        return seriesBeginIndex;
    }

    @Override
    public int getEndIndex() {
        return seriesEndIndex;
    }

    @Override
    public int getMaximumBarCount() {
        return maximumBarCount;
    }

    @Override
    public void setMaximumBarCount(int maximumBarCount) {
        if (constrained) {
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
        return removedBarsCount;
    }

    /**
     * @apiNote to add bar data direclty you can use
     *          {@link #addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num)}
     * @throws NullPointerException if {@code bar} is {@code null}
     */
    @Override
    public void addBar(Bar bar, boolean replace) {
        Objects.requireNonNull(bar, "bar must not be null");
        if (!checkBar(bar)) {
            throw new IllegalArgumentException(
                    String.format("Cannot add Bar with data type: %s to series with data" + "type: %s",
                            bar.getClosePrice().getClass(), one().getClass()));
        }
        if (!bars.isEmpty()) {
            if (replace) {
                bars.set(bars.size() - 1, bar);
                return;
            }
            final int lastBarIndex = bars.size() - 1;
            ZonedDateTime seriesEndTime = bars.get(lastBarIndex).getEndTime();
            if (!bar.getEndTime().isAfter(seriesEndTime)) {
                throw new IllegalArgumentException(
                        String.format("Cannot add a bar with end time:%s that is <= to series end time: %s",
                                bar.getEndTime(), seriesEndTime));
            }
        }

        bars.add(bar);
        if (seriesBeginIndex == -1) {
            // The begin index is set to 0 if not already initialized:
            seriesBeginIndex = 0;
        }
        seriesEndIndex++;
        removeExceedingBars();
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime) {
        this.addBar(new BaseBar(timePeriod, endTime, function()));
    }

    @Override
    public void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume) {
        this.addBar(
                new BaseBar(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, zero()));
    }

    @Override
    public void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume,
            Num amount) {
        this.addBar(
                new BaseBar(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount));
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, zero()));
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume, Num amount) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount));
    }

    @Override
    public void addTrade(Number price, Number amount) {
        addTrade(numOf(price), numOf(amount));
    }

    @Override
    public void addTrade(String price, String amount) {
        addTrade(numOf(new BigDecimal(price)), numOf(new BigDecimal(amount)));
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        getLastBar().addTrade(tradeVolume, tradePrice);
    }

    @Override
    public void addPrice(Num price) {
        getLastBar().addPrice(price);
    }

    /**
     * Removes the first N bars that exceed the {@link #maximumBarCount}.
     */
    private void removeExceedingBars() {
        int barCount = bars.size();
        if (barCount > maximumBarCount) {
            // Removing old bars
            int nbBarsToRemove = barCount - maximumBarCount;
            if (nbBarsToRemove == 1) {
                bars.remove(0);
            } else {
                bars.subList(0, nbBarsToRemove).clear();
            }
            // Updating removed bars count
            removedBarsCount += nbBarsToRemove;
            seriesBeginIndex = Math.max(seriesBeginIndex, removedBarsCount);
        }
    }

}
