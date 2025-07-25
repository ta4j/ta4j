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
 * A tick bar is sampled after a fixed number of ticks.
 */
public class TickBarBuilder implements BarBuilder {

    private final NumFactory numFactory;
    private final int tickCount;
    private int passedTicksCount;
    private BarSeries barSeries;
    private Duration timePeriod;
    private Instant beginTime;
    private Instant endTime;
    private Num volume;
    private Num openPrice;
    private Num highPrice;
    private Num closePrice;
    private Num lowPrice;
    private Num amount;
    private long trades;

    /**
     * A builder to build a new {@link BaseBar} with {@link DoubleNumFactory}
     *
     * @param tickCount the number of ticks at which a new bar should be created
     */
    public TickBarBuilder(final int tickCount) {
        this(DoubleNumFactory.getInstance(), tickCount);
    }

    /**
     * A builder to build a new {@link BaseBar}
     *
     * @param numFactory
     * @param tickCount  the number of ticks at which a new bar should be created
     */
    public TickBarBuilder(final NumFactory numFactory, final int tickCount) {
        this.numFactory = numFactory;
        this.tickCount = tickCount;
        reset();
    }

    @Override
    public BarBuilder timePeriod(final Duration timePeriod) {
        this.timePeriod = this.timePeriod == null ? timePeriod : this.timePeriod.plus(timePeriod);
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
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder openPrice(final Number openPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder openPrice(final String openPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final Number highPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final String highPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final Num highPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final Num lowPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final Number lowPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final String lowPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder closePrice(final Num tickPrice) {
        closePrice = tickPrice;
        if (openPrice == null) {
            openPrice = tickPrice;
        }

        highPrice = highPrice.max(tickPrice);
        lowPrice = lowPrice.min(tickPrice);

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
        this.volume = this.volume.plus(volume);
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
    public TickBarBuilder bindTo(final BarSeries barSeries) {
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
        if (++passedTicksCount % tickCount == 0) {
            if (amount == null && volume != null) {
                amount = closePrice.multipliedBy(volume);
            }

            barSeries.addBar(build());
            reset();
        }
    }

    private void reset() {
        final var zero = numFactory.zero();
        timePeriod = null;
        openPrice = null;
        highPrice = zero;
        lowPrice = numFactory.numOf(Integer.MAX_VALUE);
        closePrice = null;
        amount = null;
        trades = 0;
        volume = zero;
    }
}
