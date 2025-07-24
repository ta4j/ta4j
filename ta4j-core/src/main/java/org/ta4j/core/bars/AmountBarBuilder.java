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
 * An amount bar is sampled after a fixed number of amount (= price * volume)
 * have been traded.
 */
public class AmountBarBuilder implements BarBuilder {

    private Num distinctVolume;
    private final Num amountThreshold;
    private final boolean setAmountByVolume;

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
     * @param amountThreshold   the threshold at which a new bar should be created
     * @param setAmountByVolume if {@code true} the {@link #amount} is set by
     *                          {@link #volume} * {@link #closePrice}, otherwise
     *                          {@link #amount} must be explicitly set
     */
    public AmountBarBuilder(final int amountThreshold, final boolean setAmountByVolume) {
        this(DoubleNumFactory.getInstance(), amountThreshold, setAmountByVolume);
    }

    /**
     * A builder to build a new {@link BaseBar}
     *
     * @param numFactory
     * @param amountThreshold   the threshold at which a new bar should be created
     * @param setAmountByVolume if {@code true} the {@link #amount} is set by
     *                          {@link #volume} * {@link #closePrice}, otherwise
     *                          {@link #amount} must be explicitly set
     */
    public AmountBarBuilder(final NumFactory numFactory, final int amountThreshold, final boolean setAmountByVolume) {
        this.numFactory = numFactory;
        this.amountThreshold = numFactory.numOf(amountThreshold);
        this.setAmountByVolume = setAmountByVolume;
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
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
    }

    @Override
    public BarBuilder openPrice(final Number openPrice) {
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
    }

    @Override
    public BarBuilder openPrice(final String openPrice) {
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final Number highPrice) {
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final String highPrice) {
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final Num highPrice) {
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final Num lowPrice) {
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final Number lowPrice) {
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final String lowPrice) {
        throw new IllegalArgumentException("AmountBar can only be built from closePrice");
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
        this.distinctVolume = volume;
        this.volume = this.volume == null ? volume : this.volume.plus(volume);
        return this;
    }

    @Override
    public BarBuilder volume(final Number volume) {
        volume(numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder volume(final String volume) {
        volume(numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder amount(final Num amount) {
        if (setAmountByVolume) {
            throw new IllegalArgumentException("AmountBar.amount can only be built from closePrice*volume");
        }
        this.amount = this.amount == null ? amount : this.amount.plus(amount);
        return this;
    }

    @Override
    public BarBuilder amount(final Number amount) {
        amount(numFactory.numOf(amount));
        return this;
    }

    @Override
    public BarBuilder amount(final String amount) {
        amount(numFactory.numOf(amount));
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
    public AmountBarBuilder bindTo(final BarSeries barSeries) {
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

        if (setAmountByVolume) {
            final var calculatedAmount = closePrice.multipliedBy(distinctVolume);
            amount = amount == null ? calculatedAmount : amount.plus(calculatedAmount);
        }

        if (amount.isGreaterThanOrEqual(amountThreshold)) {
            // move amount remainder to next bar
            var amountRemainder = numFactory.zero();
            // move volume remainder to next bar
            var volumeRemainder = numFactory.zero();
            if (amount.isGreaterThan(amountThreshold)) {
                amountRemainder = amount.minus(amountThreshold);
                volumeRemainder = amountRemainder.dividedBy(closePrice);

                // cap currently built bar, amount is then restored to amountRemainder
                amount = amountThreshold;
                // cap currently built bar, volume is then restored to volumeRemainder
                volume = volume.minus(volumeRemainder);
            }

            barSeries.addBar(build());
            amount = amountRemainder;
            volume = volumeRemainder;

            reset();
        }
    }

    private void reset() {
        distinctVolume = null;

        timePeriod = null;
        openPrice = null;
        highPrice = numFactory.zero();
        lowPrice = numFactory.numOf(Integer.MAX_VALUE);
        closePrice = null;
        trades = 0;
    }
}
