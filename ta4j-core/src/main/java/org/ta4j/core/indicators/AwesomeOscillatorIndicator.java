/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.average.SMAIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Awesome oscillator (AO) indicator.
 *
 * @see https://www.tradingview.com/wiki/Awesome_Oscillator_(AO)
 */
public class AwesomeOscillatorIndicator extends AbstractIndicator<Num> {

    private final SMAIndicator shortSma;
    private final SMAIndicator longSma;
    private Num value;
    private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());

    /**
     * Constructor.
     *
     * @param indicator     (normally {@link MedianPriceIndicator})
     * @param shortBarCount (normally 5)
     * @param longBarCOunt  (normally 34)
     */
    public AwesomeOscillatorIndicator(final Indicator<Num> indicator, final int shortBarCount, final int longBarCOunt) {
        super(indicator.getBarSeries());
        this.shortSma = new SMAIndicator(indicator, shortBarCount);
        this.longSma = new SMAIndicator(indicator, longBarCOunt);
    }

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code barCountSma1} = 5
     * <li>{@code barCountSma2} = 34
     * </ul>
     *
     * @param indicator (normally {@link MedianPriceIndicator})
     */
    public AwesomeOscillatorIndicator(final Indicator<Num> indicator) {
        this(indicator, 5, 34);
    }

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code indicator} = {@link MedianPriceIndicator}
     * <li>{@code barCountSma1} = 5
     * <li>{@code barCountSma2} = 34
     * </ul>
     *
     * @param series the bar series
     */
    public AwesomeOscillatorIndicator(final BarSeries series) {
        this(new MedianPriceIndicator(series), 5, 34);
    }

    protected Num calculate() {
        return this.shortSma.getValue().minus(this.longSma.getValue());
    }

    @Override
    public Num getValue() {
        return this.value;
    }

    @Override
    public void refresh(final ZonedDateTime tick) {
        if (tick.isAfter(this.currentTick)) {
            this.shortSma.refresh(tick);
            this.longSma.refresh(tick);
            this.value = calculate();
            this.currentTick = tick;
        }
    }

    @Override
    public boolean isStable() {
        return this.shortSma.isStable() && this.longSma.isStable();
    }
}
