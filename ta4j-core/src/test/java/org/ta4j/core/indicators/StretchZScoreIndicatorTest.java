/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.statistics.ZScoreIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class StretchZScoreIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public StretchZScoreIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        addClose(10);
        addClose(11);
        addClose(12);
        addClose(13);
        addClose(20);
    }

    @Test
    public void shouldMatchEquivalentZScoreComposition() {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        StretchZScoreIndicator subject = new StretchZScoreIndicator(close, 3);
        Indicator<Num> deviation = NumericIndicator.of(close).minus(new SMAIndicator(close, 3));
        ZScoreIndicator expected = new ZScoreIndicator(deviation, new StandardDeviationIndicator(deviation, 3));

        assertThat(subject.getCountOfUnstableBars()).isEqualTo(expected.getCountOfUnstableBars());
        assertNumEquals(expected.getValue(4), subject.getValue(4));
    }

    @Test
    public void shouldSupportCustomReferenceIndicators() {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ConstantIndicator<Num> reference = new ConstantIndicator<>(series, numFactory.numOf(10));
        StretchZScoreIndicator subject = new StretchZScoreIndicator(close, reference, 3);

        assertThat(subject.getValue(4).isPositive()).isTrue();
    }

    private void addClose(double price) {
        series.barBuilder().openPrice(price).highPrice(price).lowPrice(price).closePrice(price).volume(1).add();
    }
}
