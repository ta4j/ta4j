/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import static org.assertj.core.api.Assertions.assertThat;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Testing the RWILowIndicator
 */
public class RWILowIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    /**
     * TODO: Just graphically Excel-Sheet validation with hard coded results. Excel
     * formula needed
     */
    private final ExternalIndicatorTest xls;

    public RWILowIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new RWILowIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "RWIHL.xls", 9, numFactory);
    }

    @Test
    public void dependenciesExposeEachCalculatedAtr() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 5; i++) {
            series.barBuilder().openPrice(10).highPrice(12).lowPrice(8).closePrice(10).add();
        }
        RWILowIndicator rwi = new RWILowIndicator(series, 3);
        assertThat(rwi.getDependencies()).hasSize(2);
        for (int i = 0; i < 2; i++) {
            ATRIndicator atr = (ATRIndicator) rwi.getDependencies().get(i);
            assertThat(atr.getCountOfUnstableBars()).isEqualTo(i + 2);
            assertThat(atr.getValue(4)).isEqualByComparingTo(numOf(4));
        }
        assertThat(rwi.getValue(4)).isEqualByComparingTo(numFactory.one().dividedBy(numOf(2).sqrt()));
        assertThat(new RWILowIndicator(series, 1).getDependencies()).isEmpty();
    }

    @Test
    public void randomWalkIndexHigh() throws Exception {
        Indicator<Num> expected = xls.getIndicator(20);
        RWILowIndicator rwil = (RWILowIndicator) getIndicator(expected.getBarSeries(), 20);
        assertEquals(20, rwil.getCountOfUnstableBars());
        assertIndicatorEquals(expected, rwil);
    }
}
