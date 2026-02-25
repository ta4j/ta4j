/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AlligatorIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public AlligatorIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 1; i <= 60; i++) {
            series.barBuilder().openPrice(i).closePrice(i).highPrice(i + 1).lowPrice(i - 1).volume(10 + i).add();
        }
    }

    @Test
    public void shouldMatchDisplacedSmmaForJawTeethAndLips() {
        final var median = new MedianPriceIndicator(series);
        final var jaw = AlligatorIndicator.jaw(series);
        final var teeth = AlligatorIndicator.teeth(series);
        final var lips = AlligatorIndicator.lips(series);

        final var jawSmma = new SMMAIndicator(median, AlligatorIndicator.JAW_BAR_COUNT);
        final var teethSmma = new SMMAIndicator(median, AlligatorIndicator.TEETH_BAR_COUNT);
        final var lipsSmma = new SMMAIndicator(median, AlligatorIndicator.LIPS_BAR_COUNT);

        assertThat(jaw.getCountOfUnstableBars())
                .isEqualTo(jawSmma.getCountOfUnstableBars() + AlligatorIndicator.JAW_SHIFT);
        assertThat(teeth.getCountOfUnstableBars())
                .isEqualTo(teethSmma.getCountOfUnstableBars() + AlligatorIndicator.TEETH_SHIFT);
        assertThat(lips.getCountOfUnstableBars())
                .isEqualTo(lipsSmma.getCountOfUnstableBars() + AlligatorIndicator.LIPS_SHIFT);

        for (int i = jaw.getCountOfUnstableBars(); i <= series.getEndIndex(); i++) {
            assertThat(jaw.getValue(i)).isEqualByComparingTo(jawSmma.getValue(i - AlligatorIndicator.JAW_SHIFT));
        }
        for (int i = teeth.getCountOfUnstableBars(); i <= series.getEndIndex(); i++) {
            assertThat(teeth.getValue(i)).isEqualByComparingTo(teethSmma.getValue(i - AlligatorIndicator.TEETH_SHIFT));
        }
        for (int i = lips.getCountOfUnstableBars(); i <= series.getEndIndex(); i++) {
            assertThat(lips.getValue(i)).isEqualByComparingTo(lipsSmma.getValue(i - AlligatorIndicator.LIPS_SHIFT));
        }
    }

    @Test
    public void shouldRespectWarmupBoundary() {
        final var line = new AlligatorIndicator(series, 3, 2);
        final int unstableBars = line.getCountOfUnstableBars();

        assertThat(line.getValue(unstableBars - 1).isNaN()).isTrue();
        assertThat(line.getValue(unstableBars).isNaN()).isFalse();
    }

    @Test
    public void shouldSupportCustomSourceIndicator() {
        final var close = new ClosePriceIndicator(series);
        final var line = new AlligatorIndicator(close, 4, 1);
        final var expected = new SMMAIndicator(close, 4);

        for (int i = line.getCountOfUnstableBars(); i <= series.getEndIndex(); i++) {
            assertThat(line.getValue(i)).isEqualByComparingTo(expected.getValue(i - 1));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        final var original = new AlligatorIndicator(series, 5, 2);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(AlligatorIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final Num expected = original.getValue(i);
            final Num actual = restored.getValue(i);
            if (expected.isNaN()) {
                assertThat(actual.isNaN()).isTrue();
            } else {
                assertThat(actual).isEqualByComparingTo(expected);
            }
        }
    }
}
