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
 * Acceleration-deceleration indicator.
 */
public class AccelerationDecelerationIndicator extends AbstractIndicator<Double> {

    private final AwesomeOscillatorIndicator awesome;
    private final SMAIndicator sma;
    private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
    private Num value;

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param shortBarCount the bar count for {@link #awesome}
     * @param longBarCount  the bar count for {@link #sma}
     */
    public AccelerationDecelerationIndicator(final BarSeries series, final int shortBarCount, final int longBarCount) {
        super(series);
        this.awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), shortBarCount, longBarCount);
        this.sma = new SMAIndicator(this.awesome, shortBarCount);
    }

    /**
     * Constructor with {@code barCountSma1} = 5 and {@code barCountSma2} = 34.
     *
     * @param series the bar series
     */
    public AccelerationDecelerationIndicator(final BarSeries series) {
        this(series, 5, 34);
    }

    protected Num calculate() {
        return this.awesome.getValue().minus(this.sma.getValue());
    }

    @Override
    public Double getValue() {
        return this.value.doubleValue();
    }

    @Override
    public void refresh(final ZonedDateTime tick) {
        if (tick.isAfter(this.currentTick)) {
            this.awesome.refresh(tick);
            this.sma.refresh(tick);
            this.value = calculate();
            this.currentTick = tick;
        }
    }

    @Override
    public boolean isStable() {
        return this.awesome.isStable() && this.sma.isStable();
    }
}
