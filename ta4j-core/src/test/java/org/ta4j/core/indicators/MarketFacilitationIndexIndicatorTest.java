/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MarketFacilitationIndexIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public MarketFacilitationIndexIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
    }

    @Test
    public void shouldComputePriceRangePerVolume() {
        series.barBuilder().openPrice(10).closePrice(10).highPrice(12).lowPrice(10).volume(4).add(); // 0.5
        series.barBuilder().openPrice(10).closePrice(10).highPrice(15).lowPrice(9).volume(3).add(); // 2.0
        series.barBuilder().openPrice(10).closePrice(10).highPrice(20).lowPrice(16).volume(8).add(); // 0.5

        final var indicator = new MarketFacilitationIndexIndicator(series);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(0);
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf("0.5"));
        assertThat(indicator.getValue(1)).isEqualByComparingTo(numFactory.two());
        assertThat(indicator.getValue(2)).isEqualByComparingTo(numFactory.numOf("0.5"));
    }

    @Test
    public void shouldReturnNaNWhenVolumeIsZero() {
        series.barBuilder().openPrice(10).closePrice(10).highPrice(12).lowPrice(10).volume(0).add();
        final var indicator = new MarketFacilitationIndexIndicator(series);

        assertThat(indicator.getValue(0).isNaN()).isTrue();
    }

    @Test
    public void shouldReturnZeroForFlatPriceSequence() {
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(5).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(7).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(9).add();

        final var indicator = new MarketFacilitationIndexIndicator(series);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(indicator.getValue(i)).isEqualByComparingTo(numFactory.zero());
        }
    }

    @Test
    public void shouldPropagateNaNInputValues() {
        series.barBuilder().openPrice(10).closePrice(10).highPrice(12).lowPrice(10).volume(4).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(13).lowPrice(9).volume(5).add();

        final Indicator<Num> high = new HighPriceIndicator(series);
        final Indicator<Num> low = new LowPriceIndicator(series);
        final Indicator<Num> volume = new VolumeIndicator(series);

        final Indicator<Num> highWithNaN = new Indicator<>() {
            @Override
            public Num getValue(int index) {
                return index == 1 ? NaN : high.getValue(index);
            }

            @Override
            public BarSeries getBarSeries() {
                return high.getBarSeries();
            }

            @Override
            public int getCountOfUnstableBars() {
                return high.getCountOfUnstableBars();
            }
        };

        final var indicator = new MarketFacilitationIndexIndicator(highWithNaN, low, volume);
        assertThat(indicator.getValue(0).isNaN()).isFalse();
        assertThat(indicator.getValue(1).isNaN()).isTrue();
    }

    @Test
    public void shouldRejectDifferentBarSeries() {
        final BarSeries anotherSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        anotherSeries.barBuilder().openPrice(1).closePrice(1).highPrice(2).lowPrice(0).volume(1).add();

        final var high = new HighPriceIndicator(series);
        final var low = new LowPriceIndicator(series);
        final var volumeDifferentSeries = new VolumeIndicator(anotherSeries);

        assertThrows(IllegalArgumentException.class,
                () -> new MarketFacilitationIndexIndicator(high, low, volumeDifferentSeries));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        series.barBuilder().openPrice(10).closePrice(10).highPrice(12).lowPrice(10).volume(4).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(15).lowPrice(9).volume(3).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(20).lowPrice(16).volume(8).add();

        final var original = new MarketFacilitationIndexIndicator(series);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(MarketFacilitationIndexIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualByComparingTo(original.getValue(i));
        }
    }
}
