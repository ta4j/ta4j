package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TrendLineSupportIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrendLineSupportIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldReturnNaNUntilTwoSwingLowsConfirmed() {
        final var series = seriesFromLows(12, 11, 9, 10, 13, 8, 9, 11);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        for (int i = 0; i <= 5; i++) {
            assertThat(indicator.getValue(i).isNaN()).isTrue();
        }

        assertThat(indicator.getValue(6).isNaN()).isFalse();
        assertThat(indicator.getPivotIndexes()).containsExactly(2, 5);
    }

    @Test
    public void shouldProjectExpectedSupportLine() {
        final var series = seriesFromLows(9, 7, 10, 11, 12, 6, 9, 13);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        for (int i = 0; i <= 6; i++) {
            indicator.getValue(i);
        }

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(1);
        final var x2 = numFactory.numOf(5);
        final var y1 = lowIndicator.getValue(1);
        final var y2 = lowIndicator.getValue(5);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y2.minus(slope.multipliedBy(x2));
        final var expected = slope.multipliedBy(numFactory.numOf(6)).plus(intercept);

        assertThat(indicator.getValue(6)).isEqualByComparingTo(expected);
        assertThat(indicator.getPivotIndexes()).containsExactly(1, 5);
    }

    @Test
    public void shouldRemainStableAcrossEqualLowPlateau() {
        final var series = seriesFromLows(13, 12, 9, 11, 10, 8, 8, 8, 12, 14);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 2);

        for (int i = 0; i <= 7; i++) {
            assertThat(indicator.getValue(i).isNaN()).isTrue();
        }

        final var valueAtEight = indicator.getValue(8);
        final var valueAtNine = indicator.getValue(9);

        assertThat(valueAtEight.isNaN()).isFalse();
        assertThat(valueAtNine.isNaN()).isFalse();
        assertThat(indicator.getPivotIndexes()).containsExactly(2, 7);

        final var numFactory = series.numFactory();
        final var x1 = numFactory.numOf(2);
        final var x2 = numFactory.numOf(7);
        final var y1 = lowIndicator.getValue(2);
        final var y2 = lowIndicator.getValue(7);
        final var slope = y2.minus(y1).dividedBy(x2.minus(x1));
        final var intercept = y2.minus(slope.multipliedBy(x2));

        assertThat(valueAtEight).isEqualByComparingTo(slope.multipliedBy(numFactory.numOf(8)).plus(intercept));
        assertThat(valueAtNine).isEqualByComparingTo(slope.multipliedBy(numFactory.numOf(9)).plus(intercept));
    }

    @Test
    public void shouldPurgeCachedPivotsWhenSeriesLosesLeadingBars() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        series.setMaximumBarCount(5);
        final var lowIndicator = new LowPriceIndicator(series);
        final var indicator = new TrendLineSupportIndicator(lowIndicator, 1, 1, 0);

        final double[] lows = { 12, 11, 9, 10, 13, 8, 9, 11, 7, 10, 12 };
        for (double low : lows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
            indicator.getValue(series.getEndIndex());
        }

        final int beginIndex = series.getBeginIndex();
        assertThat(beginIndex).isGreaterThan(0);
        assertThat(indicator.getPivotIndexes()).allMatch(pivotIndex -> pivotIndex >= beginIndex);
        assertThat(indicator.getValue(series.getEndIndex()).isNaN()).isFalse();
    }

    private BarSeries seriesFromLows(double... lows) {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        for (double low : lows) {
            final var high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        return series;
    }
}
