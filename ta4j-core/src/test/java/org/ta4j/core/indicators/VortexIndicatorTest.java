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
 * Tests for {@link VortexIndicator}.
 */
public class VortexIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    public VortexIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesPublishedReferenceValues() {
        // Reference checkpoints from:
        // https://github.com/DaveSkender/Stock.Indicators/blob/main/tests/indicators/s-z/Vortex/Vortex.Tests.cs
        BarSeries series = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        VortexIndicator indicator = new VortexIndicator(series, 14);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(14);
        assertThat(Num.isNaNOrNull(indicator.getPositiveValue(13))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getNegativeValue(13))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(13))).isTrue();

        assertNumEquals(1.0460, indicator.getPositiveValue(14));
        assertNumEquals(0.8119, indicator.getNegativeValue(14));
        assertNumEquals(0.2341, indicator.getValue(14));

        assertNumEquals(1.1300, indicator.getPositiveValue(29));
        assertNumEquals(0.7393, indicator.getNegativeValue(29));
        assertNumEquals(0.3907, indicator.getValue(29));
    }

    @Test
    public void usesDefaultLookbackPeriod() {
        BarSeries series = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        VortexIndicator defaultIndicator = new VortexIndicator(series);
        VortexIndicator explicitIndicator = new VortexIndicator(series, 14);

        assertThat(defaultIndicator.getCountOfUnstableBars()).isEqualTo(14);
        assertSameNumOrNaN(defaultIndicator.getPositiveValue(29), explicitIndicator.getPositiveValue(29));
        assertSameNumOrNaN(defaultIndicator.getNegativeValue(29), explicitIndicator.getNegativeValue(29));
        assertSameNumOrNaN(defaultIndicator.getValue(29), explicitIndicator.getValue(29));
    }

    @Test
    public void doesNotLookAhead() {
        BarSeries fullSeries = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        BarSeries truncatedSeries = PublishedReferenceSeries.copyUntilIndex(fullSeries, numFactory, 29);

        VortexIndicator fullIndicator = new VortexIndicator(fullSeries, 14);
        VortexIndicator truncatedIndicator = new VortexIndicator(truncatedSeries, 14);

        assertSameNumOrNaN(fullIndicator.getPositiveValue(29), truncatedIndicator.getPositiveValue(29));
        assertSameNumOrNaN(fullIndicator.getNegativeValue(29), truncatedIndicator.getNegativeValue(29));
        assertSameNumOrNaN(fullIndicator.getValue(29), truncatedIndicator.getValue(29));
    }

    @Test
    public void returnsNaNForShortDataWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10; i++) {
            series.barBuilder().openPrice(100 + i).highPrice(101 + i).lowPrice(99 + i).closePrice(100 + i).add();
        }

        VortexIndicator indicator = new VortexIndicator(series, 14);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(Num.isNaNOrNull(indicator.getPositiveValue(i))).isTrue();
            assertThat(Num.isNaNOrNull(indicator.getNegativeValue(i))).isTrue();
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void returnsNaNForFlatMarket() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 40; i++) {
            series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).add();
        }

        VortexIndicator indicator = new VortexIndicator(series, 14);
        int unstableBars = indicator.getCountOfUnstableBars();
        assertThat(unstableBars).isEqualTo(14);
        assertThat(Num.isNaNOrNull(indicator.getValue(unstableBars - 1))).isTrue();

        for (int i = unstableBars; i <= series.getEndIndex(); i++) {
            assertThat(Num.isNaNOrNull(indicator.getPositiveValue(i))).isTrue();
            assertThat(Num.isNaNOrNull(indicator.getNegativeValue(i))).isTrue();
            assertThat(Num.isNaNOrNull(indicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void throwsForInvalidLookbackPeriod() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();

        assertThrows(IllegalArgumentException.class, () -> new VortexIndicator(series, 1));
        assertThrows(IllegalArgumentException.class, () -> new VortexIndicator(series, 0));
        assertThrows(IllegalArgumentException.class, () -> new VortexIndicator(series, -4));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesAllSeries() {
        BarSeries series = PublishedReferenceSeries.stockIndicatorsDefaultSeries(numFactory);
        VortexIndicator original = new VortexIndicator(series, 14);

        String json = original.toJson();
        Indicator<Num> reconstructedBase = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(reconstructedBase).isInstanceOf(VortexIndicator.class);
        assertThat(reconstructedBase.toDescriptor()).isEqualTo(original.toDescriptor());
        assertThat(reconstructedBase.getCountOfUnstableBars()).isEqualTo(original.getCountOfUnstableBars());

        VortexIndicator reconstructed = (VortexIndicator) reconstructedBase;
        for (int i = original.getCountOfUnstableBars(); i <= series.getEndIndex(); i++) {
            assertSameNumOrNaN(original.getPositiveValue(i), reconstructed.getPositiveValue(i));
            assertSameNumOrNaN(original.getNegativeValue(i), reconstructed.getNegativeValue(i));
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
