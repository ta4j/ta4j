/*
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
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class InvertedHammerIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries downtrendSeries;
    private BarSeries uptrendSeries;

    public InvertedHammerIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        final var downtrend = generateDowntrend();
        final var uptrend = generateUptrend();

        final var invertedHammer = new MockBarBuilder(numFactory).openPrice(10d)
                .closePrice(5d)
                .highPrice(100d)
                .lowPrice(0d)
                .build();
        downtrend.add(invertedHammer);
        uptrend.add(invertedHammer);

        this.downtrendSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withBars(downtrend).build();
        this.uptrendSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withBars(uptrend).build();
    }

    private List<Bar> generateDowntrend() {
        final var bars = new ArrayList<Bar>(26);
        for (int i = 26; i > 0; --i) {
            bars.add(new MockBarBuilder(numFactory).openPrice(i).closePrice(i).highPrice(i).lowPrice(i).build());
        }

        return bars;
    }

    private List<Bar> generateUptrend() {
        final var bars = new ArrayList<Bar>(26);
        for (int i = 0; i < 26; ++i) {
            bars.add(new MockBarBuilder(numFactory).openPrice(i).closePrice(i).highPrice(i).lowPrice(i).build());
        }

        return bars;
    }

    @Test
    public void getValueAsInvertedHammer() {
        final var invertedHammer = new InvertedHammerIndicator(this.downtrendSeries);
        assertTrue(invertedHammer.getValue(26));
    }

    @Test
    public void getValueNonInvertedHammer() {
        final var invertedHammer = new InvertedHammerIndicator(this.downtrendSeries);
        assertFalse(invertedHammer.getValue(25));
    }

    @Test
    public void getValueInvertedHammerInUptrend() {
        final var invertedHammer = new InvertedHammerIndicator(this.uptrendSeries);
        assertFalse(invertedHammer.getValue(26));
    }
}
