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
package org.ta4j.core.indicators.aroon;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.candles.price.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Aroon down indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon">chart_school:technical_indicators:aroon</a>
 */
public class AroonDownIndicator extends AbstractIndicator<Num> {

    private final int barCount;
    private final LowestValueIndicator lowestLowValueIndicator;
    private final Indicator<Num> lowIndicator;

    private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
    private int index;
    private final ArrayList<Num> previousValues;
    private Num value;

    /**
     * Constructor.
     *
     * @param lowIndicator the indicator for the low price (default
     *                     {@link LowPriceIndicator})
     * @param barCount     the time frame
     */
    public AroonDownIndicator(final Indicator<Num> lowIndicator, final int barCount) {
        super(lowIndicator.getBarSeries());
        this.barCount = barCount;
        this.previousValues = new ArrayList<>(barCount);
        for (int i = 0; i < barCount; i++) {
          this.previousValues.add(NaN.NaN);
        }
        this.lowIndicator = lowIndicator;
        this.lowestLowValueIndicator = new LowestValueIndicator(lowIndicator, barCount + 1);
    }

    /**
     * Default Constructor with {@code lowPriceIndicator} =
     * {@link LowPriceIndicator}.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public AroonDownIndicator(final BarSeries series, final int barCount) {
        this(new LowPriceIndicator(series), barCount);
    }

    protected Num calculate() {
        final var currentLow = this.lowIndicator.getValue();
        this.previousValues.set(getIndex(this.index), currentLow);

        if (currentLow.isNaN()) {
            return NaN.NaN;
        }

        final var lowestValue = this.lowestLowValueIndicator.getValue();

        final var barCountFromLastMinimum = countBarsBetweenLows(lowestValue);
        final var numFactory = getBarSeries().numFactory();
        return numFactory.numOf((double)(this.barCount -  barCountFromLastMinimum) / this.barCount * 100.0);
    }

    private int countBarsBetweenLows(final Num lowestValue) {
        for (int i = getIndex(this.index), barDistance = 0; barDistance < this.barCount; barDistance++, i--) {
            if (this.previousValues.get(getIndex(this.barCount + i)).equals(lowestValue)) {
                return barDistance;
            }
        }
        return this.barCount;
    }

    private int getIndex(final int i) {
        return i % this.barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + this.barCount;
    }

    @Override
    public Num getValue() {
        return this.value;
    }

    @Override
    public void refresh(final ZonedDateTime tick) {
        if (tick.isAfter(this.currentTick)) {
            ++this.index;
            this.lowIndicator.refresh(tick);
            this.lowestLowValueIndicator.refresh(tick);
            this.value = calculate();
            this.currentTick = tick;
        }
    }

    @Override
    public boolean isStable() {
        return this.index > this.barCount && this.lowIndicator.isStable() && this.lowestLowValueIndicator.isStable();
    }
}
