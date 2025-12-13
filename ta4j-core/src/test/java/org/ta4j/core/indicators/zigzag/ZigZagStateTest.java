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
package org.ta4j.core.indicators.zigzag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ZigZagStateTest {

    private final NumFactory numFactory = org.ta4j.core.num.DecimalNumFactory.getInstance();

    @Test
    public void shouldStoreAllStateValues() {
        final Num highPrice = numFactory.numOf(100.0);
        final Num lowPrice = numFactory.numOf(50.0);
        final Num extremePrice = numFactory.numOf(95.0);

        final ZigZagState state = new ZigZagState(5, highPrice, 3, lowPrice, ZigZagTrend.UP, 7, extremePrice);

        assertThat(state.getLastHighIndex()).isEqualTo(5);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(highPrice);
        assertThat(state.getLastLowIndex()).isEqualTo(3);
        assertThat(state.getLastLowPrice()).isEqualByComparingTo(lowPrice);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(state.getLastExtremeIndex()).isEqualTo(7);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(extremePrice);
    }

    @Test
    public void shouldHandleUninitializedState() {
        final Num extremePrice = numFactory.numOf(75.0);
        final ZigZagState state = new ZigZagState(-1, null, -1, null, ZigZagTrend.UNDEFINED, 0, extremePrice);

        assertThat(state.getLastHighIndex()).isEqualTo(-1);
        assertThat(state.getLastHighPrice()).isNull();
        assertThat(state.getLastLowIndex()).isEqualTo(-1);
        assertThat(state.getLastLowPrice()).isNull();
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UNDEFINED);
        assertThat(state.getLastExtremeIndex()).isEqualTo(0);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(extremePrice);
    }

    @Test
    public void shouldHandleDownTrend() {
        final Num highPrice = numFactory.numOf(100.0);
        final Num lowPrice = numFactory.numOf(50.0);
        final Num extremePrice = numFactory.numOf(45.0);

        final ZigZagState state = new ZigZagState(5, highPrice, 8, lowPrice, ZigZagTrend.DOWN, 9, extremePrice);

        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.DOWN);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(extremePrice);
    }

    @Test
    public void shouldHandleUpTrend() {
        final Num highPrice = numFactory.numOf(100.0);
        final Num lowPrice = numFactory.numOf(50.0);
        final Num extremePrice = numFactory.numOf(105.0);

        final ZigZagState state = new ZigZagState(5, highPrice, 3, lowPrice, ZigZagTrend.UP, 7, extremePrice);

        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(extremePrice);
    }
}
