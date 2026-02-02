/*
 * SPDX-License-Identifier: MIT
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
