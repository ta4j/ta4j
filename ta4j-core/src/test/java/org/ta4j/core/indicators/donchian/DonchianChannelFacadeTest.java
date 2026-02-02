/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.donchian;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DonchianChannelFacadeTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public DonchianChannelFacadeTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = new MockBarSeriesBuilder().withName("DonchianChannelFacadeTestSeries")
                .withNumFactory(numFactory)
                .build();

        series.barBuilder().openPrice(100d).highPrice(105d).lowPrice(95d).closePrice(100d).add();
        series.barBuilder().openPrice(105).highPrice(110).lowPrice(100).closePrice(105).add();
        series.barBuilder().openPrice(110).highPrice(115).lowPrice(105).closePrice(110).add();
        series.barBuilder().openPrice(115).highPrice(120).lowPrice(110).closePrice(115).add();
        series.barBuilder().openPrice(120).highPrice(125).lowPrice(115).closePrice(120).add();
        series.barBuilder().openPrice(115).highPrice(120).lowPrice(110).closePrice(115).add();
        series.barBuilder().openPrice(110).highPrice(115).lowPrice(105).closePrice(110).add();
        series.barBuilder().openPrice(105).highPrice(110).lowPrice(100).closePrice(105).add();
        series.barBuilder().openPrice(100).highPrice(105).lowPrice(95).closePrice(100).add();
    }

    @Test
    public void testNumericFacadesSameAsDefaultIndicators() {
        var donchianChannelMiddle = new DonchianChannelMiddleIndicator(series, 3);
        var donchianChannelUpper = new DonchianChannelUpperIndicator(series, 3);
        var donchianChannelLower = new DonchianChannelLowerIndicator(series, 3);
        var donchianChannelFacade = new DonchianChannelFacade(series, 3);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(donchianChannelFacade.lower().getValue(i), donchianChannelLower.getValue(i));
            assertNumEquals(donchianChannelFacade.middle().getValue(i), donchianChannelMiddle.getValue(i));
            assertNumEquals(donchianChannelFacade.upper().getValue(i), donchianChannelUpper.getValue(i));
        }
    }

}
