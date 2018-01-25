/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Num.AbstractNum;
import org.ta4j.core.Num.BigDecimalNum;
import org.ta4j.core.Num.DoubleNum;
import org.ta4j.core.Num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Base implementation of a {@link TimeSeries}.
 * <p></p>
 */
public class BaseTimeSeries implements TimeSeries {

    private static final long serialVersionUID = -1878027009398790126L;
    /** Name for unnamed series */
    private static final String UNNAMED_SERIES_NAME = "unamed_series";
    /** The logger */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** Name of the series */
    private final String name;
    /** Begin index of the time series */
    private int seriesBeginIndex = -1;
    /** End index of the time series */
    private int seriesEndIndex = -1;
    /** List of bars */
    private final List<Bar> bars;
    /** Maximum number of bars for the time series */
    private int maximumBarCount = Integer.MAX_VALUE;
    /** Number of removed bars */
    private int removedBarsCount = 0;
    /** True if the current series is constrained (i.e. its indexes cannot change), false otherwise */
    private boolean constrained = false;

    private final Function<Number, Num> numFunction;

    /**
     * Constructor of an unnamed series.
     */
    public BaseTimeSeries() {
        this(UNNAMED_SERIES_NAME);
    }

    /**
     * Constructor.
     * @param name the name of the series
     */
    public BaseTimeSeries(String name) {
        this(name, new ArrayList<Bar>());
    }

    /**
     * Constructor of an unnamed series.
     * @param bars the list of bars of the series
     */
    public BaseTimeSeries(List<Bar> bars) {
        this(UNNAMED_SERIES_NAME, bars);
    }

    /**
     * Constructor.
     * @param name the name of the series
     * @param bars the list of bars of the series
     */
    public BaseTimeSeries(String name, List<Bar> bars) {
        this(name, bars, 0, bars.size() - 1, false);
    }

    /**
     * Constructor.
     * <p>
     * Constructs a constrained time series from an original one.
     * @param defaultSeries the original time series to construct a constrained series from
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex the end index (inclusive) of the time series
     * @deprecated use {@link #getSubSeries(int, int) getSubSeries(startIndex, endIndex)} to satisfy correct behaviour of
     * the new sub series in further calculations
     */
    @Deprecated
    public BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex) {
        this(defaultSeries.getName(), defaultSeries.getBarData(), seriesBeginIndex, seriesEndIndex, true, defaultSeries.getNumFunction());
        if (defaultSeries.getBarData() == null || defaultSeries.getBarData().isEmpty()) {
            throw new IllegalArgumentException("Cannot create a constrained series from a time series with a null/empty list of bars");
        }
        if (defaultSeries.getMaximumBarCount() != Integer.MAX_VALUE) {
            throw new IllegalStateException("Cannot create a constrained series from a time series for which a maximum bar count has been set");
        }
    }

    /**
     * Constructor.<p/>
     * Creates a BaseTimeSeries with default {@link BigDecimalNum BigDecimal} as type for the data and all operations on it
     * @param name the name of the series
     * @param bars the list of bars of the series
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex the end index (inclusive) of the time series
     * @param constrained true to constrain the time series (i.e. indexes cannot change), false otherwise
     *
     *
     */
    private BaseTimeSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained) {
        this(name, bars, seriesBeginIndex, seriesEndIndex, constrained, BigDecimalNum::valueOf);
    }

    /**
     * Constructor.
     * @param name the name of the series
     * @param bars the list of bars of the series
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex the end index (inclusive) of the time series
     * @param constrained true to constrain the time series (i.e. indexes cannot change), false otherwise
     * @param numFunction a {@link Function} to convert a {@link Number} to a {@link Num Num implementation}
     */
    private BaseTimeSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained, Function<Number, Num> numFunction) {
        this.name = name;
        this.numFunction = numFunction;
        this.bars = bars;
        if (bars.isEmpty()) {
        	// Bar list empty
            this.seriesBeginIndex = -1;
            this.seriesEndIndex = -1;
            this.constrained = false;
            return;
        }

        // Bar list not empty: checking num types
        if(!checkBars(bars)){
            throw new IllegalArgumentException("The Num implementation of bars does not match to the Num implementation of time series");
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
     * Returns a new BaseTimeSeries that is a subset of this BaseTimeSeries.
     * The new series holds a copy of all {@link Bar bars} between <tt>startIndex</tt> (inclusive) and <tt>endIndex</tt> (exclusive)
     * of this TimeSeries.
     * The indices of this TimeSeries and the new subset TimeSeries can be different. I. e. index 0 of the new TimeSeries will
     * be index <tt>startIndex</tt> of this TimeSeries.
     * If <tt>startIndex</tt> < this.seriesBeginIndex the new TimeSeries will start with the first available Bar of this TimeSeries.
     * If <tt>endIndex</tt> > this.seriesEndIndex+1 the new TimeSeries will end at the last available Bar of this TimeSeries
     * @param startIndex the startIndex
     * @param endIndex the endIndex (exclusive)
     * @return a new BaseTimeSeries with Bars from <tt>startIndex</tt> to <tt>endIndex</tt>-1
     * @throws IllegalArgumentException if <tt>endIndex</tt> < <tt>startIndex</tt>
     */
    @Override
    public TimeSeries getSubSeries(int startIndex, int endIndex){
        if(startIndex > endIndex){
            throw new IllegalArgumentException
                    (String.format("the endIndex: %s must be bigger than startIndex: %s", endIndex, startIndex));
        }
        if(!bars.isEmpty()) {
            int start = Math.max(startIndex, this.seriesBeginIndex);
            int end = Math.min(endIndex, this.seriesEndIndex + 1);
            return new BaseTimeSeries(getName(), cut(bars, start, end));
        }
        return new BaseTimeSeries(name);

    }

    @Override
    public Num valueOf(Number number){
        return this.numFunction.apply(number);
    }

    @Override
    public Function<Number, Num> getNumFunction() {
        return numFunction;
    }

    /**
     * Checks if all {@link Bar bars} of a list fits to the {@link Num NumFunction} used by this time series.
     * @param bars a List of Bar objects.
     * @return false if a Num implementation of at least one Bar does not fit.
     */
    private boolean checkBars(List<Bar> bars){
        for(Bar bar: bars){
            if(!checkBar(bar)){
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the {@link Num} implementation of a {@link Bar} fits to the NumFunction used by time series.
     * @param bar a Bar object.
     * @return false if another Num implementation is used than by this time series.
     * @see Num
     * @see Bar
     * @see #addBar(Duration, ZonedDateTime)
     */
    private boolean checkBar(Bar bar){
        if(bar.getClosePrice()==null){
            return true; // bar has not been initialized with data (deprecated constructor)
        }
        // all other constructors initialize at least the close price
        return numFunction.apply(1).getClass() == bar.getClosePrice().getClass();
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
    public int getMaximumBarCount() {
        return maximumBarCount;
    }

    @Override
    public int getRemovedBarsCount() {
        return removedBarsCount;
    }

    /**
     * @deprecated will be private in next release. Use other {@link #addBar(Duration, ZonedDateTime) addBar function}
     * @param bar the bar to be added
     */
    @Override
    @Deprecated
    public void addBar(Bar bar) {
        if (bar == null) {
            throw new IllegalArgumentException("Cannot add null bar");
        }

        if (!bars.isEmpty()) {
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
            // Begin index set to 0 only if if wasn't initialized
            seriesBeginIndex = 0;
        }
        seriesEndIndex++;
        removeExceedingBars();
    }

    @Override
    @SuppressWarnings( "deprecation" ) // will also work with private addBar function, converts to Num before calling constructor
    public void addBar(Duration timePeriod, ZonedDateTime endTime) {
        this.addBar(new BaseBar(timePeriod,endTime, getNumFunction()));
    }

    @Override
    @SuppressWarnings( "deprecation" ) // will also work with private addBar function, converts to Num before calling constructor
    public void addBar(ZonedDateTime endTime, double openPrice, double highPrice, double lowPrice, double closePrice, double volume) {
        this.addBar(new BaseBar(endTime, valueOf(openPrice), valueOf(highPrice), valueOf(lowPrice), valueOf(closePrice),
                valueOf(volume), valueOf(0)));
    }

    @Override
    @SuppressWarnings( "deprecation" ) // will also work with private addBar function
    public void addBar(ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice, String closePrice, String volume) {
        this.addBar(new BaseBar(endTime, valueOf(new BigDecimal(openPrice)), valueOf(new BigDecimal(highPrice)),
                valueOf(new BigDecimal(lowPrice)), valueOf(new BigDecimal(closePrice)), valueOf(new BigDecimal(volume)),
                valueOf(0)));
    }

    @Override
    @SuppressWarnings( "deprecation" ) // will also work with private addBar function
    public void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume) {
        this.addBar(new BaseBar(endTime, openPrice,highPrice,lowPrice,closePrice,volume, valueOf(0)));
    }

    @Override
    @SuppressWarnings( "deprecation" ) // will also work with private addBar function
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, valueOf(0)));
    }

    @Override
    @SuppressWarnings( "deprecation" ) // will also work with private addBar function
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice,highPrice,lowPrice,closePrice,volume, amount));
    }

    @Override
    public void addTrade(double price, double amount) {
        addTrade(valueOf(price), valueOf(amount));
    }

    @Override
    public void addTrade(String price, String amount) {
        addTrade(valueOf(new BigDecimal(price)), valueOf(new BigDecimal(amount)));
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        getLastBar().addTrade(tradeVolume,tradePrice);
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

    /**
     * Cuts a list of bars into a new list of bars that is a subset of it
     * @param bars the list of {@link Bar bars}
     * @param startIndex start index of the subset
     * @param endIndex end index of the subset
     * @return a new list of bars with tick from startIndex (inclusive) to endIndex (exclusive)
     */
    private static List<Bar> cut(List<Bar> bars, final int startIndex, final int endIndex){
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    /**
     * @param series a time series
     * @param index an out of bounds bar index
     * @return a message for an OutOfBoundsException
     */
    private static String buildOutOfBoundsMessage(BaseTimeSeries series, int index) {
        return "Size of series: " + series.bars.size() + " bars, "
                + series.removedBarsCount + " bars removed, index = " + index;
    }

    public static class SeriesBuilder implements TimeSeriesBuilder {

        private static final long serialVersionUID = 111164611841087550L;

        private List<Bar> bars;
        private String name;
        private Function<Number, Num> numFunction;

        private boolean isConstrained;
        private int beginIndex;
        private int endIndex;
        private int maxBarCount;

        public SeriesBuilder(){
            initValues();
        }

        private void initValues() {
            bars = new ArrayList<>();
            name = "unnamed_series";
            numFunction = BigDecimalNum::valueOf;
            isConstrained = false;
            beginIndex = -1;
            endIndex = -1;
            maxBarCount = Integer.MAX_VALUE;
        }

        @Override
        public TimeSeries build() {
            TimeSeries series = new BaseTimeSeries(name,bars,beginIndex,endIndex,isConstrained,numFunction);
            series.setMaximumBarCount(maxBarCount);
            initValues();
            return series;
        }

        public SeriesBuilder setConstrained(boolean isConstrained){
            this.isConstrained = true;
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

        public SeriesBuilder withMaxBarCount(int maxBarCount){
            this.maxBarCount = maxBarCount;
            return this;
        }

        public SeriesBuilder withNumTypeOf(AbstractNum type) {
            numFunction = type.getNumFunction();
            return this;
        }

        public SeriesBuilder withNumTypeOf(Function<Number, Num> function) {
            numFunction = function;
            return this;
        }

        public SeriesBuilder withNumTypeOf(Class<? extends AbstractNum> abstractNumClass) {
            if(abstractNumClass==BigDecimalNum.class){
                numFunction = BigDecimalNum::valueOf;
                return this;
            } else if(abstractNumClass== DoubleNum.class){
                numFunction = DoubleNum::valueOf;
                return this;
            }
            numFunction = BigDecimalNum::valueOf;
            return this;
        }
    }
}
