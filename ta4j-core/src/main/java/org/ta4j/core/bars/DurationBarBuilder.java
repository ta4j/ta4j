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
package org.ta4j.core.bars;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A duration bar is sampled after a fixed duration.
 */
public class DurationBarBuilder implements BarBuilder {

    private final Duration duration;
    private Duration passedDuration;
    private Num firstOpenPrice;

    private final NumFactory numFactory;
    private BarSeries barSeries;
    private Duration timePeriod;
    private Instant beginTime;
    private Instant endTime;
    private Num openPrice;
    private Num highPrice;
    private Num lowPrice;
    private Num closePrice;
    private Num volume;
    private Num amount;
    private long trades;

    /**
     * A builder to build a new {@link BaseBar} with {@link DoubleNumFactory}
     *
     * @param duration the time period at which a new bar should be created
     */
    public DurationBarBuilder(final Duration duration) {
        this(DoubleNumFactory.getInstance(), duration);
    }

    /**
     * A builder to build a new {@link BaseBar}
     *
     * @param numFactory
     * @param duration   the time period at which a new bar should be created
     */
    public DurationBarBuilder(final NumFactory numFactory, final Duration duration) {
        this.numFactory = numFactory;
        this.duration = duration;
        reset();
    }

    @Override
    public BarBuilder timePeriod(final Duration timePeriod) {
        this.timePeriod = timePeriod;
        return this;
    }

    @Override
    public BarBuilder beginTime(final Instant beginTime) {
        this.beginTime = beginTime;
        return this;
    }

    @Override
    public BarBuilder endTime(final Instant endTime) {
        this.endTime = endTime;
        return this;
    }

    @Override
    public BarBuilder openPrice(final Num openPrice) {
        if (firstOpenPrice == null) {
            firstOpenPrice = openPrice;
        }
        this.openPrice = firstOpenPrice;
        return this;
    }

    @Override
    public BarBuilder openPrice(final Number openPrice) {
        return openPrice(numFactory.numOf(openPrice));
    }

    @Override
    public BarBuilder openPrice(final String openPrice) {
        return openPrice(numFactory.numOf(openPrice));
    }

    @Override
    public BarBuilder highPrice(final Num highPrice) {
        this.highPrice = this.highPrice == null ? highPrice : this.highPrice.max(highPrice);
        return this;
    }

    @Override
    public BarBuilder highPrice(final Number highPrice) {
        return highPrice(numFactory.numOf(highPrice));
    }

    @Override
    public BarBuilder highPrice(final String highPrice) {
        return highPrice(numFactory.numOf(highPrice));
    }

    @Override
    public BarBuilder lowPrice(final Num lowPrice) {
        this.lowPrice = this.lowPrice == null ? lowPrice : this.lowPrice.min(lowPrice);
        return this;
    }

    @Override
    public BarBuilder lowPrice(final Number lowPrice) {
        return lowPrice(numFactory.numOf(lowPrice));
    }

    @Override
    public BarBuilder lowPrice(final String lowPrice) {
        return lowPrice(numFactory.numOf(lowPrice));
    }

    @Override
    public BarBuilder closePrice(final Num closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    @Override
    public BarBuilder closePrice(final Number closePrice) {
        return closePrice(numFactory.numOf(closePrice));
    }

    @Override
    public BarBuilder closePrice(final String closePrice) {
        return closePrice(numFactory.numOf(closePrice));
    }

    @Override
    public BarBuilder volume(final Num volume) {
        this.volume = this.volume == null ? volume : this.volume.plus(volume);
        return this;
    }

    @Override
    public BarBuilder volume(final Number volume) {
        volume(this.numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder volume(final String volume) {
        volume(this.numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder amount(final Num amount) {
        this.amount = this.amount == null ? amount : this.amount.plus(amount);
        return this;
    }

    @Override
    public BarBuilder amount(final Number amount) {
        amount(this.numFactory.numOf(amount));
        return this;
    }

    @Override
    public BarBuilder amount(final String amount) {
        amount(this.numFactory.numOf(amount));
        return this;
    }

    @Override
    public BarBuilder trades(final long trades) {
        this.trades += trades;
        return this;
    }

    @Override
    public BarBuilder trades(final String trades) {
        trades(Long.parseLong(trades));
        return this;
    }

    @Override
    public DurationBarBuilder bindTo(final BarSeries barSeries) {
        this.barSeries = Objects.requireNonNull(barSeries);
        return this;
    }

    /**
     * Builds bar from current state that is modified for each tick.
     *
     * @return snapshot of current state
     */
    @Override
    public Bar build() {
        return new BaseBar(timePeriod, beginTime, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount,
                trades);
    }

    @Override
    public void add() {

        // set timePeriod of this bar
        if (timePeriod == null) {
            Objects.requireNonNull(beginTime);
            Objects.requireNonNull(endTime);
            timePeriod = Duration.between(beginTime, endTime);
        }

        // check if timePeriod is valid for aggregation
        final boolean isMultiplication = duration.getSeconds() % timePeriod.getSeconds() == 0;
        if (!isMultiplication) {
            throw new IllegalArgumentException(
                    "Cannot aggregate bars: bar.timePeriod must be a multiplication of the given duration.");
        }

        // remember the accumulated duration
        passedDuration = passedDuration.plus(timePeriod);

        if (passedDuration.compareTo(duration) == 0) {
            // each aggregated bar has the same (upscaled) duration
            this.timePeriod = duration;

            if (amount == null && volume != null) {
                amount = closePrice.multipliedBy(volume);
            }

            if (endTime != null) {
                beginTime = endTime.minus(duration);
            } else {
                Objects.requireNonNull(beginTime);
                endTime = beginTime.plus(duration);
            }

            barSeries.addBar(build());
            reset();
        }
    }

    private void reset() {
        passedDuration = Duration.ZERO;
        firstOpenPrice = null;

        timePeriod = null;
        beginTime = null;
        endTime = null;
        openPrice = null;
        highPrice = null;
        lowPrice = null;
        closePrice = null;
        volume = null;
        amount = null;
        trades = 0;
    }

}
