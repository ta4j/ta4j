/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class StochasticRSIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private final ExternalIndicatorTest xls;
    private BarSeries data;

    public StochasticRSIIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new StochasticRSIIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "AAPL_StochRSI.xls", 15, numFactory);
    }

    @Test
    public void xlsTest() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
        Indicator<Num> xlsClose = new ClosePriceIndicator(xlsSeries);
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsClose, 14);
        assertNumEquals(52.23449323656383, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex() - 1));
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(50.45, 50.30, 50.20, 50.15, 50.05, 50.06, 50.10, 50.08, 50.03, 50.07, 50.01, 50.14, 50.22,
                        50.43, 50.50, 50.56, 50.52, 50.70, 50.55, 50.62, 50.90, 50.82, 50.86, 51.20, 51.30, 51.10,
                        51.25, 51.35)
                .build();
    }

    @Test
    public void stochasticRSI() {
        var subject = new StochasticRSIIndicator(data, 14);
        int unstableBars = subject.getCountOfUnstableBars();
        assertThat(unstableBars).isEqualTo(27);
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(subject.getValue(i))).isTrue();
        }

        assertThat(Num.isNaNOrNull(subject.getValue(unstableBars))).isFalse();
    }

    @Test
    public void testStochasticRSIWithClearMinMax() {
        // Test data: RSI values will be [100, 0, 100, 0] over 3-period
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 15, 10, 15, 10, 15, 10).build();
        var subject = new StochasticRSIIndicator(data, 3);

        int unstableBars = subject.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(subject.getValue(i))).isTrue();
        }
        assertThat(Num.isNaNOrNull(subject.getValue(unstableBars))).isFalse();
    }

    @Test
    public void testStochasticRSIWithEqualMinMax() {
        // Test data: RSI values will be [100, 100, 100] over 3-period
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20, 20, 20, 20, 20).build();
        var subject = new StochasticRSIIndicator(data, 3);

        int unstableBars = subject.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(subject.getValue(i))).isTrue();
        }
        for (int i = unstableBars; i < data.getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(subject.getValue(i))).isTrue();
        }
    }

    @Test
    public void testCalculateReturnsNaNForIndicesWithinUnstablePeriod() {
        int barCount = 14;
        Indicator<Num> subject = new StochasticRSIIndicator(new ClosePriceIndicator(data), barCount);

        int unstableBars = subject.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertEquals(NaN.NaN, subject.getValue(i));
        }
    }

    @Test
    public void testGetCountOfUnstableBarsMatchesBarCount() {
        int barCount = 5;
        Indicator<Num> subject = new StochasticRSIIndicator(new RSIIndicator(new ClosePriceIndicator(data), barCount),
                barCount);

        assertEquals(barCount + barCount - 1, subject.getCountOfUnstableBars());
    }
}
