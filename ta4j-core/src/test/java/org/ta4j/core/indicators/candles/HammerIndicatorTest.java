/*
 * SPDX-License-Identifier: MIT
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

public class HammerIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries downtrendSeries;
    private BarSeries uptrendSeries;

    public HammerIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        final var downtrend = generateDowntrend();
        final var uptrend = generateUptrend();

        final var hammer = new MockBarBuilder(numFactory).openPrice(95d)
                .closePrice(90d)
                .highPrice(100d)
                .lowPrice(0d)
                .build();
        downtrend.add(hammer);
        uptrend.add(hammer);

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
    public void getValueAsHammer() {
        final var hammer = new HammerIndicator(this.downtrendSeries);
        assertTrue(hammer.getValue(26));
    }

    @Test
    public void getValueNonHammer() {
        final var hammer = new HammerIndicator(this.downtrendSeries);
        assertFalse(hammer.getValue(25));
    }

    @Test
    public void getValueHammerInUptrend() {
        final var hammer = new HammerIndicator(this.uptrendSeries);
        assertFalse(hammer.getValue(26));
    }
}
