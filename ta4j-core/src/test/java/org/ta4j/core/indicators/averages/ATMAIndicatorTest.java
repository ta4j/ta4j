/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.*;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.CsvTestUtils;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ATMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ATMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void atmaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(ATMAIndicatorTest.class, "ATMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        ATMAIndicator atma = new ATMAIndicator(new ClosePriceIndicator(barSeries), 10);

        for (int i = 10; i < barSeries.getBarCount(); i++) {
            barSeries.getBar(i).getClosePrice();

            assertNumEquals(mock.getValue(i).doubleValue(), atma.getValue(i));
        }
    }

    @Test
    public void oddBarCountUsesRoundedUpFastSmoothingLength() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7)
                .build();
        ATMAIndicator atma = new ATMAIndicator(new ClosePriceIndicator(barSeries), 5);

        assertThat(atma.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(Num.isNaNOrNull(atma.getValue(3))).isTrue();
        assertNumEquals(3, atma.getValue(4));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        return List.of(serializationFixture(series, new ATMAIndicator(close, 7), stableIndexes(series)));
    }

}
