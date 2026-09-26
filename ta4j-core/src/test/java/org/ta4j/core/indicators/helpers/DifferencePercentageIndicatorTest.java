/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.StochasticIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@SuppressWarnings("deprecation")
public class DifferencePercentageIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public DifferencePercentageIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void retainedHeadAdvanceReanchorsThresholdState() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        StochasticIndicator source = new StochasticIndicator(new ClosePriceIndicator(series), 3);
        DifferencePercentageIndicator existing = new DifferencePercentageIndicator(source, numOf(10));

        assertThat(Num.isFinite(existing.getValue(series.getEndIndex()))).isTrue();
        series.setMaximumBarCount(3);
        series.barBuilder().openPrice(60).closePrice(60).highPrice(60).lowPrice(60).volume(0).add();

        StochasticIndicator freshSource = new StochasticIndicator(new ClosePriceIndicator(series), 3);
        DifferencePercentageIndicator fresh = new DifferencePercentageIndicator(freshSource, numOf(10));
        int endIndex = series.getEndIndex();

        assertThat(fresh.getValue(endIndex).isNaN()).isTrue();
        assertThat(existing.getValue(endIndex).isNaN()).isTrue();
    }
}
