/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DoubleEMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceIndicator closePrice;

    public DoubleEMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0.73, 0.72, 0.86, 0.72, 0.62, 0.76, 0.84, 0.69, 0.65, 0.71, 0.53, 0.73, 0.77, 0.67, 0.68)
                .build();
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void doubleEMAUsingBarCount5UsingClosePrice() {
        var doubleEma = new DoubleEMAIndicator(closePrice, 5);

        int unstableBars = doubleEma.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(doubleEma.getValue(i))).isTrue();
        }

        for (int i = unstableBars; i < closePrice.getBarSeries().getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(doubleEma.getValue(i))).isFalse();
        }
    }

    @Test
    public void primesBothStagesWhileItsPublicValueIsUnstable() {
        BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        moving.setMaximumBarCount(2);
        DoubleEMAIndicator indicator = new DoubleEMAIndicator(new ClosePriceIndicator(moving), 3);
        TraceTestLogger capture = new TraceTestLogger();
        capture.open();
        try {
            for (int index = 0; index <= 6; index++) {
                moving.barBuilder().closePrice(10 + index).add();
                Num actual = indicator.getValue(index);
                if (index < 6) {
                    assertThat(actual.isNaN()).isTrue();
                } else {
                    // EMA values: 15.125 and 14.4375 after seeding both at 13.
                    TestUtils.assertNumEquals(15.8125, actual);
                }
            }
        } finally {
            capture.close();
        }
    }
}
