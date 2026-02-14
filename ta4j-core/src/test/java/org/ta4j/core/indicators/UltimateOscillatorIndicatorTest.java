/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Tests for {@link UltimateOscillatorIndicator}.
 */
public class UltimateOscillatorIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public UltimateOscillatorIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesPublishedReferenceValues() {
        // Reference checkpoints from:
        // https://github.com/DaveSkender/Stock.Indicators/blob/main/tests/indicators/s-z/Ultimate/Ultimate.Tests.cs
        BarSeries series = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        UltimateOscillatorIndicator indicator = new UltimateOscillatorIndicator(series, 7, 14, 28);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(28);
        assertThat(Num.isNaNOrNull(indicator.getValue(27))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(28))).isFalse();

        assertNumEquals(51.7770, indicator.getValue(74));
    }

    @Test
    public void usesDefaultPeriods() {
        BarSeries series = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        UltimateOscillatorIndicator defaultIndicator = new UltimateOscillatorIndicator(series);
        UltimateOscillatorIndicator explicitIndicator = new UltimateOscillatorIndicator(series, 7, 14, 28);

        assertThat(defaultIndicator.getCountOfUnstableBars()).isEqualTo(28);
        assertSameNumOrNaN(defaultIndicator.getValue(74), explicitIndicator.getValue(74));
    }

    @Test
    public void supportsConfigurablePeriods() {
        BarSeries series = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        UltimateOscillatorIndicator indicator = new UltimateOscillatorIndicator(series, 4, 8, 16);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(16);
        assertThat(Num.isNaNOrNull(indicator.getValue(15))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(16))).isFalse();
    }

    @Test
    public void doesNotLookAhead() {
        BarSeries fullSeries = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        BarSeries truncatedSeries = PublishedReferenceSeries.copyUntilIndex(fullSeries, numFactory, 74);

        UltimateOscillatorIndicator fullIndicator = new UltimateOscillatorIndicator(fullSeries, 7, 14, 28);
        UltimateOscillatorIndicator truncatedIndicator = new UltimateOscillatorIndicator(truncatedSeries, 7, 14, 28);

        assertSameNumOrNaN(fullIndicator.getValue(74), truncatedIndicator.getValue(74));
    }

    @Test
    public void returnsNaNForShortDataWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 20; i++) {
            series.barBuilder().openPrice(100 + i).highPrice(101 + i).lowPrice(99 + i).closePrice(100 + i).add();
        }

        UltimateOscillatorIndicator indicator = new UltimateOscillatorIndicator(series, 7, 14, 28);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void returnsNaNForFlatMarket() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 50; i++) {
            series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).add();
        }

        UltimateOscillatorIndicator indicator = new UltimateOscillatorIndicator(series, 7, 14, 28);
        int unstableBars = indicator.getCountOfUnstableBars();
        assertThat(unstableBars).isEqualTo(28);
        assertThat(Num.isNaNOrNull(indicator.getValue(unstableBars - 1))).isTrue();

        for (int i = unstableBars; i <= series.getEndIndex(); i++) {
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void throwsForInvalidPeriods() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();

        assertThrows(IllegalArgumentException.class, () -> new UltimateOscillatorIndicator(series, 0, 14, 28));
        assertThrows(IllegalArgumentException.class, () -> new UltimateOscillatorIndicator(series, 7, 7, 28));
        assertThrows(IllegalArgumentException.class, () -> new UltimateOscillatorIndicator(series, 7, 14, 14));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        BarSeries series = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        UltimateOscillatorIndicator original = new UltimateOscillatorIndicator(series, 7, 14, 28);

        String json = original.toJson();
        Indicator<Num> reconstructedBase = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(reconstructedBase).isInstanceOf(UltimateOscillatorIndicator.class);
        assertThat(reconstructedBase.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(reconstructedBase.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        UltimateOscillatorIndicator reconstructed = (UltimateOscillatorIndicator) reconstructedBase;
        for (int i = original.getCountOfUnstableBars(); i <= series.getEndIndex(); i++) {
            assertSameNumOrNaN(original.getValue(i), reconstructed.getValue(i));
        }
    }

    private void assertSameNumOrNaN(Num expected, Num actual) {
        if (Num.isNaNOrNull(expected) || Num.isNaNOrNull(actual)) {
            assertThat(Num.isNaNOrNull(actual)).isEqualTo(Num.isNaNOrNull(expected));
            return;
        }
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
