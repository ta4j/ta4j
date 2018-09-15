/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Base implementation of a {@link TimeSeries}.
 * </p>
 */
public class BaseTimeSeries implements TimeSeries {

    private static final long serialVersionUID = -1878027009398790126L;
    /**
     * Name for unnamed series
     */
    private static final String UNNAMED_SERIES_NAME = "unamed_series";
    /**
     * Num type function
     **/
    protected final Function<Number, Num> numFunction;
    /**
     * The logger
     */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * Name of the series
     */
    private final String name;
    /**
     * List of bars
     */
    private final List<Bar> bars;
    /**
     * Begin index of the time series
     */
    private int seriesBeginIndex;
    /**
     * End index of the time series
     */
    private int seriesEndIndex;
    /**
     * Maximum number of bars for the time series
     */
    private int maximumBarCount = Integer.MAX_VALUE;
    /**
     * Number of removed bars
     */
    private int removedBarsCount = 0;
    /**
     * True if the current series is constrained (i.e. its indexes cannot change), false otherwise
     */
    private boolean constrained;

    /**
     * Constructor of an unnamed series.
     */
    public BaseTimeSeries() {
        this(UNNAMED_SERIES_NAME);
    }

    /**
     * Constructor.
     *
     * @param name the name of the series
     */
    public BaseTimeSeries(String name) {
        this(name, new ArrayList<>());
    }

    /**
     * Constructor of an unnamed series.
     *
     * @param bars the list of bars of the series
     */
    public BaseTimeSeries(List<Bar> bars) {
        this(UNNAMED_SERIES_NAME, bars);
    }

    /**
     * Constructor.
     *
     * @param name the name of the series
     * @param bars the list of bars of the series
     */
    public BaseTimeSeries(String name, List<Bar> bars) {
        this(name, bars, 0, bars.size() - 1, false);
    }

    /**
     * Constructor.
     *
     * @param name the name of the series
     */
    public BaseTimeSeries(String name, Function<Number, Num> numFunction) {
        this(name, new ArrayList<>(), numFunction);
    }

    /**
     * Constructor.
     *
     * @param name the name of the series
     * @param bars the list of bars of the series
     */
    public BaseTimeSeries(String name, List<Bar> bars, Function<Number, Num> numFunction) {
        this(name, bars, 0, bars.size() - 1, false, numFunction);
    }

    /**
     * Constructor.<p/>
     * Creates a BaseTimeSeries with default {@link PrecisionNum} as type for the data and all operations on it
     *
     * @param name             the name of the series
     * @param bars             the list of bars of the series
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex   the end index (inclusive) of the time series
     * @param constrained      true to constrain the time series (i.e. indexes cannot change), false otherwise
     */
    private BaseTimeSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained) {
        this(name, bars, seriesBeginIndex, seriesEndIndex, constrained, PrecisionNum::valueOf);
    }


    /**
     * Constructor.
     *
     * @param name             the name of the series
     * @param bars             the list of bars of the series
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex   the end index (inclusive) of the time series
     * @param constrained      true to constrain the time series (i.e. indexes cannot change), false otherwise
     * @param numFunction      a {@link Function} to convert a {@link Number} to a {@link Num Num implementation}
     */
    private BaseTimeSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained,
            Function<Number, Num> numFunction) {
        this.name = name;

        this.bars = bars;
        if (bars.isEmpty()) {
            // Bar list empty
            this.seriesBeginIndex = -1;
            this.seriesEndIndex = -1;
            this.constrained = false;
            this.numFunction = numFunction;
            return;
        }
        // Bar list not empty: take Function of first bar
        this.numFunction = bars.get(0).getClosePrice().function();
        // Bar list not empty: checking num types
        if (!checkBars(bars)) {
            throw new IllegalArgumentException(
                    String.format("Num implementation of bars: %s" +
                                    " does not match to Num implementation of time series: %s",
                            bars.get(0).getClosePrice().getClass(), numFunction));
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
     * Cuts a list of bars into a new list of bars that is a subset of it
     *
     * @param bars       the list of {@link Bar bars}
     * @param startIndex start index of the subset
     * @param endIndex   end index of the subset
     * @return a new list of bars with tick from startIndex (inclusive) to endIndex (exclusive)
     */
    private static List<Bar> cut(List<Bar> bars, final int startIndex, final int endIndex) {
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    /**
     * @param series a time series
     * @param index  an out of bounds bar index
     * @return a message for an OutOfBoundsException
     */
    private static String buildOutOfBoundsMessage(BaseTimeSeries series, int index) {
        return String.format("Size of series: %s bars, %s bars removed, index = %s",
                series.bars.size(), series.removedBarsCount, index);
    }

    /**
     * Returns a new BaseTimeSeries that is a subset of this BaseTimeSeries.
     * The new series holds a copy of all {@link Bar bars} between <tt>startIndex</tt> (inclusive) and <tt>endIndex</tt> (exclusive)
     * of this TimeSeries.
     * The indices of this TimeSeries and the new subset TimeSeries can be different. I. e. index 0 of the new TimeSeries will
     * be index <tt>startIndex</tt> of this TimeSeries.
     * If <tt>startIndex</tt> < this.seriesBeginIndex the new TimeSeries will start with the first available Bar of this TimeSeries.
     * If <tt>endIndex</tt> > this.seriesEndIndex+1 the new TimeSeries will end at the last available Bar of this TimeSeries
     *
     * @param startIndex the startIndex
     * @param endIndex   the endIndex (exclusive)
     * @return a new BaseTimeSeries with Bars from <tt>startIndex</tt> to <tt>endIndex</tt>-1
     * @throws IllegalArgumentException if <tt>endIndex</tt> < <tt>startIndex</tt>
     */
    @Override
    public TimeSeries getSubSeries(int startIndex, int endIndex) {
        if (startIndex > endIndex) {
            throw new IllegalArgumentException
                    (String.format("the endIndex: %s must be bigger than startIndex: %s", endIndex, startIndex));
        }
        if (!bars.isEmpty()) {
            int start = Math.max(startIndex, this.seriesBeginIndex);
            int end = Math.min(endIndex, this.seriesEndIndex + 1);
            return new BaseTimeSeries(getName(), cut(bars, start, end), numFunction);
        }
        return new BaseTimeSeries(name, numFunction);

    }

    @Override
    public Num numOf(Number number) {
        return this.numFunction.apply(number);
    }

    @Override
    public Function<Number, Num> function() {
        return numFunction;
    }

    /**
     * Checks if all {@link Bar bars} of a list fits to the {@link Num NumFunction} used by this time series.
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
     * Checks if the {@link Num} implementation of a {@link Bar} fits to the NumFunction used by time series.
     *
     * @param bar a Bar object.
     * @return false if another Num implementation is used than by this time series.
     * @see Num
     * @see Bar
     * @see #addBar(Duration, ZonedDateTime)
     */
    private boolean checkBar(Bar bar) {
        if (bar.getClosePrice() == null) {
            return true; // bar has not been initialized with data (uses deprecated constructor)
        }
        // all other constructors initialize at least the close price, check if Num implementation fits to numFunction
        Class<? extends Num> f = numOf(1).getClass();
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
            log.trace("Time series `{}` ({} bars): bar {} already removed, use {}-th instead", name, bars.size(), i, removedBarsCount);
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
            throw new IllegalStateException("Cannot set a maximum bar count on a constrained time series");
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
     * @param bar the <code>Bar</code> to be added
     * @apiNote to add bar data directly use #addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num)
     */
    @Override
    public void addBar(Bar bar, boolean replace) {
        Objects.requireNonNull(bar);
        if (!checkBar(bar)) {
            throw new IllegalArgumentException(String.format("Cannot add Bar with data type: %s to series with data" +
                    "type: %s", bar.getClosePrice().getClass(), numOf(1).getClass()));
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
                                bar.getEndTime(),
                                seriesEndTime));
            }
        }

        bars.add(bar);
        if (seriesBeginIndex == -1) {
            // Begin index set to 0 only if it wasn't initialized
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
        this.addBar(new BaseBar(endTime, openPrice, highPrice, lowPrice, closePrice, volume, numOf(0)));
    }

    @Override
    public void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount) {
        this.addBar(new BaseBar(endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount));
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, numOf(0)));
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
     * Removes the N first bars which exceed the maximum bar count.
     */
    private void removeExceedingBars() {
        int barCount = bars.size();
        if (barCount > maximumBarCount) {
            // Removing old bars
            int nbBarsToRemove = barCount - maximumBarCount;
            for (int i = 0; i < nbBarsToRemove; i++) {
                bars.remove(0);
            }
            // Updating removed bars count
            removedBarsCount += nbBarsToRemove;
        }
    }

    public static class SeriesBuilder implements TimeSeriesBuilder {

        private static final long serialVersionUID = 111164611841087550L;
        /**
         * Default Num type function
         **/
        private static Function<Number, Num> defaultFunction = PrecisionNum::valueOf;
        private List<Bar> bars;
        private String name;
        private Function<Number, Num> numFunction;
        private boolean constrained;
        private int maxBarCount;

        public SeriesBuilder() {
            initValues();
        }

        public static void setDefaultFunction(Function<Number, Num> defaultFunction) {
            SeriesBuilder.defaultFunction = defaultFunction;
        }

        private void initValues() {
            this.bars = new ArrayList<>();
            this.name = "unnamed_series";
            this.numFunction = SeriesBuilder.defaultFunction;
            this.constrained = false;
            this.maxBarCount = Integer.MAX_VALUE;
        }

        @Override
        public TimeSeries build() {
            int beginIndex = -1;
            int endIndex = -1;
            if (!bars.isEmpty()) {
                beginIndex = 0;
                endIndex = bars.size() - 1;
            }
            TimeSeries series = new BaseTimeSeries(name, bars, beginIndex, endIndex, constrained, numFunction);
            series.setMaximumBarCount(maxBarCount);
            initValues(); // reinitialize values for next series
            return series;
        }

        public SeriesBuilder setConstrained(boolean constrained) {
            this.constrained = constrained;
            return this;
        }

        public SeriesBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public SeriesBuilder withBars(List<Bar> bars) {
            this.bars = bars;
            return this;
        }

        public SeriesBuilder withMaxBarCount(int maxBarCount) {
            this.maxBarCount = maxBarCount;
            return this;
        }

        public SeriesBuilder withNumTypeOf(Num type) {
            numFunction = type.function();
            return this;
        }

        public SeriesBuilder withNumTypeOf(Function<Number, Num> function) {
            numFunction = function;
            return this;
        }

        public SeriesBuilder withNumTypeOf(Class<? extends Num> abstractNumClass) {
            if (abstractNumClass == PrecisionNum.class) {
                numFunction = PrecisionNum::valueOf;
                return this;
            } else if (abstractNumClass == DoubleNum.class) {
                numFunction = DoubleNum::valueOf;
                return this;
            }
            numFunction = PrecisionNum::valueOf;
            return this;
        }

    }
}
