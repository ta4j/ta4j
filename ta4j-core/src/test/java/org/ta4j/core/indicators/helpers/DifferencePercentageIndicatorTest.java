/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.NaN;
import org.ta4j.core.indicators.CachedIndicator;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    public void transientHistoricalReadDoesNotAdvanceTheNotificationState() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 115, 120, 105, 110)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        AtomicBoolean sourceFailing = new AtomicBoolean(true);
        CachedIndicator<Num> flaky = new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                if (index == 1 && sourceFailing.get()) {
                    markResultTransient();
                    return NaN.NaN;
                }
                return close.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        DifferencePercentageIndicator indicator = new DifferencePercentageIndicator(flaky, numOf(10));

        assertThat(indicator.getValue(4).isNaN()).isTrue();
        sourceFailing.set(false);

        assertThat(indicator.getValue(4).doubleValue()).isCloseTo(-4.3478, within(1e-3));
    }
}
