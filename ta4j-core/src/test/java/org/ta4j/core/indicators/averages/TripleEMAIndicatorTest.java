/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TripleEMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceIndicator closePrice;

    public TripleEMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0.73, 0.72, 0.86, 0.72, 0.62, 0.76, 0.84, 0.69, 0.65, 0.71, 0.53, 0.73, 0.77, 0.67, 0.68,
                        0.69, 0.7, 0.72, 0.74, 0.71)
                .build();
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void tripleEMAUsingBarCount5UsingClosePrice() {
        var tripleEma = new TripleEMAIndicator(closePrice, 5);

        int unstableBars = tripleEma.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(tripleEma.getValue(i))).isTrue();
        }

        for (int i = unstableBars; i < closePrice.getBarSeries().getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(tripleEma.getValue(i))).isFalse();
        }
    }

    @Test
    public void primesAllStagesWhileItsPublicValueIsUnstable() {
        BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        moving.setMaximumBarCount(2);
        TripleEMAIndicator indicator = new TripleEMAIndicator(new ClosePriceIndicator(moving), 3);
        TraceTestLogger capture = new TraceTestLogger();
        capture.open();
        try {
            for (int index = 0; index <= 9; index++) {
                moving.barBuilder().closePrice(10 + index).add();
                Num actual = indicator.getValue(index);
                if (index < 9) {
                    assertThat(actual.isNaN()).isTrue();
                } else {
                    // Exact stages: 1153/64, 1093/64 and 4153/256; TEMA = 4873/256.
                    TestUtils.assertNumEquals(19.03515625, actual);
                }
            }
        } finally {
            capture.close();
        }
    }
}
