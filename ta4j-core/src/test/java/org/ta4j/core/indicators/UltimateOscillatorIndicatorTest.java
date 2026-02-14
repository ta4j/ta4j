/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
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
        BarSeries series = publishedReferenceSeries();
        UltimateOscillatorIndicator indicator = new UltimateOscillatorIndicator(series, 7, 14, 28);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(28);
        assertThat(Num.isNaNOrNull(indicator.getValue(27))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(28))).isFalse();

        assertNumEquals(51.7770, indicator.getValue(74));
    }

    @Test
    public void usesDefaultPeriods() {
        BarSeries series = publishedReferenceSeries();
        UltimateOscillatorIndicator defaultIndicator = new UltimateOscillatorIndicator(series);
        UltimateOscillatorIndicator explicitIndicator = new UltimateOscillatorIndicator(series, 7, 14, 28);

        assertThat(defaultIndicator.getCountOfUnstableBars()).isEqualTo(28);
        assertSameNumOrNaN(defaultIndicator.getValue(74), explicitIndicator.getValue(74));
    }

    @Test
    public void supportsConfigurablePeriods() {
        BarSeries series = publishedReferenceSeries();
        UltimateOscillatorIndicator indicator = new UltimateOscillatorIndicator(series, 4, 8, 16);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(16);
        assertThat(Num.isNaNOrNull(indicator.getValue(15))).isTrue();
        assertThat(Num.isNaNOrNull(indicator.getValue(16))).isFalse();
    }

    @Test
    public void doesNotLookAhead() {
        BarSeries fullSeries = publishedReferenceSeries();
        BarSeries truncatedSeries = copyUntilIndex(fullSeries, 74);

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
    public void throwsForMismatchedSourceIndicators() {
        BarSeries firstSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
        BarSeries secondSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();

        assertThrows(IllegalArgumentException.class,
                () -> new UltimateOscillatorIndicator(new HighPriceIndicator(firstSeries),
                        new LowPriceIndicator(firstSeries), new ClosePriceIndicator(secondSeries), 7, 14, 28));
    }

    @Test
    public void throwsForNullSourceIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();

        assertThrows(NullPointerException.class, () -> new UltimateOscillatorIndicator(null,
                new LowPriceIndicator(series), new ClosePriceIndicator(series), 7, 14, 28));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTripPreservesValuesAndUnstableBars() {
        BarSeries series = publishedReferenceSeries();
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

    private BarSeries publishedReferenceSeries() {
        String[][] stockIndicatorsDefault80Ohlc = { { "212.61", "213.35", "211.52", "212.8" },
                { "213.16", "214.22", "213.15", "214.06" }, { "213.77", "214.06", "213.02", "213.89" },
                { "214.02", "215.17", "213.42", "214.66" }, { "214.38", "214.53", "213.91", "213.95" },
                { "213.97", "214.89", "213.52", "213.95" }, { "213.86", "214.55", "213.13", "214.55" },
                { "213.99", "214.22", "212.53", "214.02" }, { "214.21", "214.84", "214.17", "214.51" },
                { "213.81", "214.25", "213.33", "213.75" }, { "214.02", "214.27", "213.42", "214.22" },
                { "214.31", "214.46", "212.96", "213.43" }, { "214.18", "214.75", "213.49", "214.21" },
                { "213.85", "214.28", "212.83", "213.66" }, { "213.89", "215.48", "213.77", "215.03" },
                { "216.07", "216.89", "215.89", "216.89" }, { "216.73", "217.02", "216.36", "216.66" },
                { "216.75", "216.91", "216.12", "216.32" }, { "215.57", "215.59", "213.9", "214.98" },
                { "214.44", "215.03", "213.82", "214.96" }, { "215.65", "215.96", "214.4", "215.05" },
                { "214.65", "215.5", "214.29", "215.19" }, { "216.18", "216.87", "215.84", "216.67" },
                { "216.23", "216.66", "215.92", "216.28" }, { "216.71", "216.97", "216.09", "216.29" },
                { "215.98", "216.72", "215.7", "216.58" }, { "216.88", "218.19", "216.84", "217.86" },
                { "218.24", "218.97", "217.88", "218.72" }, { "219.26", "220.19", "219.23", "219.91" },
                { "219.71", "220.8", "219.33", "220.79" }, { "220.55", "222.15", "220.5", "221.94" },
                { "221.98", "222.16", "220.93", "221.75" }, { "221.03", "222.1", "221.01", "222.1" },
                { "222.51", "223.62", "222.5", "223.43" }, { "222.98", "223.47", "222.8", "223.23" },
                { "223.79", "223.81", "222.55", "223.38" }, { "222.45", "223.71", "222.41", "223.66" },
                { "223.57", "224.2", "223.29", "224.01" }, { "223.6", "223.86", "222.98", "223.41" },
                { "225.22", "227.04", "225.2", "226.53" }, { "226.33", "226.34", "225.05", "225.11" },
                { "225.01", "225.43", "224.6", "225.25" }, { "224.38", "224.97", "223.92", "224.58" },
                { "224.25", "224.64", "223.68", "223.91" }, { "224.23", "224.51", "223.34", "223.49" },
                { "223.62", "224.13", "222.72", "223.78" }, { "224.82", "224.87", "223.52", "224.56" },
                { "224.49", "224.72", "224.13", "224.67" }, { "224.08", "224.13", "223.14", "223.81" },
                { "224.44", "226.21", "224.18", "225.75" }, { "225.9", "225.99", "224.95", "225.31" },
                { "225.59", "225.8", "224.91", "224.91" }, { "224.91", "225.22", "224.24", "224.66" },
                { "225.33", "225.46", "221.64", "221.78" }, { "221.82", "222.61", "221.13", "222.3" },
                { "222.04", "223.31", "221.66", "222.06" }, { "222.4", "223.02", "221.05", "221.9" },
                { "220.07", "221.96", "219.77", "221.67" }, { "221.34", "223.75", "221.22", "223.29" },
                { "222.97", "223.75", "222.72", "223.5" }, { "223.43", "224.43", "223.24", "224.21" },
                { "223.84", "224.42", "223.63", "223.69" }, { "223.74", "223.96", "221.95", "223.3" },
                { "222.98", "223.53", "222.56", "223.44" }, { "224.18", "225.25", "222.55", "222.78" },
                { "222.93", "223.97", "222.44", "223.4" }, { "223.13", "223.93", "222.64", "223.17" },
                { "223.33", "224.18", "222.73", "223.31" }, { "222.89", "223.15", "221.41", "223.04" },
                { "222.74", "222.95", "221.82", "222.06" }, { "221.69", "222.5", "220.62", "220.62" },
                { "221.19", "222.58", "220.97", "222.58" }, { "221.77", "222.5", "221.16", "221.91" },
                { "222.53", "222.94", "221.26", "221.5" }, { "222.18", "223.79", "221.83", "223.31" },
                { "223.22", "223.28", "222.16", "222.6" }, { "225.05", "225.27", "222.57", "225.04" },
                { "225.75", "226.73", "225.65", "226.35" }, { "226.31", "227.28", "226.16", "226.21" },
                { "226.56", "226.73", "225.81", "226.4" } };

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (String[] row : stockIndicatorsDefault80Ohlc) {
            series.barBuilder().openPrice(row[0]).highPrice(row[1]).lowPrice(row[2]).closePrice(row[3]).add();
        }
        return series;
    }

    private BarSeries copyUntilIndex(BarSeries source, int endIndexInclusive) {
        if (endIndexInclusive < source.getBeginIndex() || endIndexInclusive > source.getEndIndex()) {
            throw new IllegalArgumentException("endIndexInclusive must be within source bounds");
        }

        BarSeries copy = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = source.getBeginIndex(); i <= endIndexInclusive; i++) {
            Bar bar = source.getBar(i);
            copy.barBuilder()
                    .openPrice(bar.getOpenPrice())
                    .highPrice(bar.getHighPrice())
                    .lowPrice(bar.getLowPrice())
                    .closePrice(bar.getClosePrice())
                    .volume(bar.getVolume())
                    .amount(bar.getAmount())
                    .trades(bar.getTrades())
                    .add();
        }
        return copy;
    }
}
