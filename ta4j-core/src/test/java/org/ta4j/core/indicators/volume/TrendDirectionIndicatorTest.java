/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Tests for {@link TrendDirectionIndicator}.
 */
public class TrendDirectionIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final double[] EXPECTED = { 1.0, 1.0, 1.0, -1.0, -1.0, -1.0, 1.0 };

    public TrendDirectionIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void carriesPreviousDirectionAcrossFlatBasisValues() {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 1d, 1d, 1d, 1d, 1d, 1d)
                .build();
        final FixedNumIndicator basis = new FixedNumIndicator(series, 10, 11, 11, 10, 9, 9, 10);

        final TrendDirectionIndicator indicator = new TrendDirectionIndicator(basis);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(1);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(indicator.getValue(i)).isEqualByComparingTo(numFactory.numOf(EXPECTED[i]));
        }
    }

    @Test
    public void highLowCloseConvenienceConstructorMatchesPositiveTrendSeries() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.bullish(numFactory);
        final TrendDirectionIndicator indicator = new TrendDirectionIndicator(series);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(indicator.getValue(i)).isEqualByComparingTo(numFactory.one());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValues() {
        final BarSeries series = VolumeSpreadsheetReferenceScenarios.sideways(numFactory);
        final TrendDirectionIndicator original = new TrendDirectionIndicator(series);

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(TrendDirectionIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(restored.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualByComparingTo(original.getValue(i));
        }
    }

    @Test
    public void throwsForMismatchedSourceIndicators() {
        final BarSeries firstSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d)
                .build();
        final BarSeries secondSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> new TrendDirectionIndicator(new HighPriceIndicator(firstSeries),
                        new LowPriceIndicator(firstSeries), new ClosePriceIndicator(secondSeries)));
    }
}
