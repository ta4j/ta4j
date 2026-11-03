/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ADXIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final ExternalIndicatorTest xls;

    public ADXIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new ADXIndicator(data, (int) params[0], (int) params[1]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 15, this.numFactory);
    }

    @Test
    public void externalData() throws Exception {
        BarSeries series = xls.getSeries();
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(series, 1, 1);
        assertEquals(3, actualIndicator.getCountOfUnstableBars());
        assertEquals(100.0, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(series, 3, 2);
        assertEquals(6, actualIndicator.getCountOfUnstableBars());
        assertEquals(12.1330, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(series, 13, 8);
        assertEquals(22, actualIndicator.getCountOfUnstableBars());
        assertEquals(7.3884, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void periodFourteenContinuesOnAWindowShorterThanItsDependencyWarmUp() {
        BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        moving.setMaximumBarCount(4);
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        ADXIndicator indicator = new ADXIndicator(moving, 14);
        ADXIndicator reference = new ADXIndicator(unbounded, 14);
        TraceTestLogger capture = new TraceTestLogger();
        capture.open();
        try {
            assertEquals(29, indicator.getCountOfUnstableBars());
            for (int index = 0; index <= 33; index++) {
                moving.barBuilder()
                        .openPrice(100 + index)
                        .highPrice(103 + index)
                        .lowPrice(98 + index)
                        .closePrice(101 + index)
                        .add();
                unbounded.barBuilder()
                        .openPrice(100 + index)
                        .highPrice(103 + index)
                        .lowPrice(98 + index)
                        .closePrice(101 + index)
                        .add();
                Num actual = indicator.getValue(index);
                Num expected = reference.getValue(index);
                TestUtils.assertNumEquals(expected, actual);
                if (index >= 29) {
                    TestUtils.assertNumEquals(100, actual);
                }
            }
        } finally {
            capture.close();
        }
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new ADXIndicator(series, 7, 5), stableIndexes(series)));
    }

}
