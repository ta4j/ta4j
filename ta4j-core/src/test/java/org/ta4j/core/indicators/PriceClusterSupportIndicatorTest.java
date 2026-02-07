/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.ta4j.core.num.NaN.NaN;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.supportresistance.PriceClusterSupportIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PriceClusterSupportIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public PriceClusterSupportIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = buildSeries(new double[] { 20, 20, 20, 12, 12, 12, 15, 15, 15 },
                new double[] { 5, 4, 6, 10, 11, 12, 7, 8, 9 });
    }

    @Test
    public void shouldRespectLookbackWindow() {
        var price = new ClosePriceIndicator(series);
        var volume = new VolumeIndicator(series, 1);
        var indicator = new PriceClusterSupportIndicator(price, volume, 3, numOf(0.25));

        assertThat(indicator.getValue(6).doubleValue()).as("lookback excludes early clusters")
                .isCloseTo(12.0, within(1e-6));
        assertThat(indicator.getClusterIndex(6)).isEqualTo(5);

        assertThat(indicator.getValue(5).doubleValue()).as("tie resolved toward lower price")
                .isCloseTo(12.0, within(1e-6));
        assertThat(indicator.getClusterIndex(5)).isEqualTo(5);
    }

    @Test
    public void shouldPropagateNaNWhenWindowContainsNoValidValues() {
        var baseIndicator = new ClosePriceIndicator(series);
        Indicator<Num> withNaN = new Indicator<>() {
            private final Set<Integer> invalidIndices = Set.of(5, 6, 7, 8);

            @Override
            public Num getValue(int index) {
                if (invalidIndices.contains(index)) {
                    return NaN;
                }
                return baseIndicator.getValue(index);
            }

            @Override
            public BarSeries getBarSeries() {
                return baseIndicator.getBarSeries();
            }

            @Override
            public int getCountOfUnstableBars() {
                return baseIndicator.getCountOfUnstableBars();
            }
        };

        var indicator = new PriceClusterSupportIndicator(withNaN, 2, numOf(0.1));

        assertThat(indicator.getValue(2)).as("valid values should survive mixed window")
                .isEqualByComparingTo(numOf(20));
        assertThat(indicator.getClusterIndex(2)).isEqualTo(2);

        assertThat(indicator.getValue(7).isNaN()).as("NaN when entire lookback is invalid").isTrue();
        assertThat(indicator.getClusterIndex(7)).isEqualTo(-1);
    }

    @Test
    public void shouldFavorVolumeHeavierClusterBeforeTieBreakers() {
        BarSeries customSeries = buildSeries(new double[] { 10, 15, 15 }, new double[] { 100, 30, 30 });
        var price = new ClosePriceIndicator(customSeries);
        var volume = new VolumeIndicator(customSeries, 1);
        var indicator = new PriceClusterSupportIndicator(price, volume, 0, numOf(0.5));

        assertThat(indicator.getValue(2)).as("support favours heavier low cluster despite smaller count")
                .isEqualByComparingTo(numOf(10));
        assertThat(indicator.getClusterIndex(2)).isEqualTo(0);
    }

    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        var indicator = new PriceClusterSupportIndicator(series, 3, numOf(0.25));

        String json = indicator.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(PriceClusterSupportIndicator.class);
        var restoredIndicator = (PriceClusterSupportIndicator) restored;
        assertThat(restoredIndicator.toDescriptor()).isEqualTo(indicator.toDescriptor());
        int index = series.getEndIndex();
        assertThat(restoredIndicator.getValue(index)).isEqualByComparingTo(indicator.getValue(index));
        assertThat(restoredIndicator.getClusterIndex(index)).isEqualTo(indicator.getClusterIndex(index));
    }

    @Test
    public void unstableBarsIncludeInputWarmupAndLookback() {
        BarSeries customSeries = buildSeries(new double[] { 10, 10, 10, 12, 12, 12, 12 },
                new double[] { 5, 5, 5, 5, 5, 5, 5 });
        MockIndicator price = new MockIndicator(customSeries, 2, numOf(10), numOf(10), numOf(10), numOf(12), numOf(12),
                numOf(12), numOf(12));
        MockIndicator volume = new MockIndicator(customSeries, 3, numOf(5), numOf(5), numOf(5), numOf(5), numOf(5),
                numOf(5), numOf(5));
        PriceClusterSupportIndicator indicator = new PriceClusterSupportIndicator(price, volume, 3, numOf(0.1));

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(5);
        assertThat(indicator.getValue(4).isNaN()).isTrue();
        assertThat(indicator.getValue(5).isNaN()).isFalse();
    }

    private BarSeries buildSeries(double[] closes, double[] volumes) {
        var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        BarSeries barSeries = builder.build();
        for (int i = 0; i < closes.length; i++) {
            barSeries.barBuilder()
                    .closePrice(closes[i])
                    .openPrice(closes[i])
                    .highPrice(closes[i])
                    .lowPrice(closes[i])
                    .volume(volumes[i])
                    .add();
        }
        return barSeries;
    }
}
