/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.supportresistance.VolumeProfileKDEIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VolumeProfileKDEIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VolumeProfileKDEIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldHighlightDominantVolumeNode() {
        BarSeries series = buildSeries(new double[] { 10, 10.5, 11 }, new double[] { 150, 60, 25 });
        var price = new ClosePriceIndicator(series);
        var volume = new VolumeIndicator(series, 1);

        var indicator = new VolumeProfileKDEIndicator(price, volume, 0, numOf(0.5));

        Num lowerDensity = indicator.getDensityAtPrice(2, numOf(10));
        Num higherDensity = indicator.getDensityAtPrice(2, numOf(11));

        assertThat(lowerDensity.isGreaterThan(higherDensity)).isTrue();
        assertThat(indicator.getModePrice(2)).isEqualByComparingTo(numOf(10));
    }

    @Test
    public void shouldRespectLookbackLength() {
        BarSeries series = buildSeries(new double[] { 10, 10.5, 11 }, new double[] { 150, 60, 25 });
        var price = new ClosePriceIndicator(series);
        var volume = new VolumeIndicator(series, 1);

        var full = new VolumeProfileKDEIndicator(price, volume, 0, numOf(0.5));
        var truncated = new VolumeProfileKDEIndicator(price, volume, 2, numOf(0.5));

        Num fullDensity = full.getDensityAtPrice(2, numOf(10));
        Num truncatedDensity = truncated.getDensityAtPrice(2, numOf(10));

        assertThat(truncatedDensity.isLessThan(fullDensity)).isTrue();
    }

    @Test
    public void shouldSumVolumesWhenBandwidthIsZero() {
        BarSeries series = buildSeries(new double[] { 10, 10, 11 }, new double[] { 100, 200, 50 });
        var price = new ClosePriceIndicator(series);
        var volume = new VolumeIndicator(series, 1);

        var indicator = new VolumeProfileKDEIndicator(price, volume, 0, numOf(0));

        assertThat(indicator.getDensityAtPrice(2, numOf(10))).isEqualByComparingTo(numOf(300));
        assertThat(indicator.getDensityAtPrice(2, numOf(11))).isEqualByComparingTo(numOf(50));
    }

    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        BarSeries series = buildSeries(new double[] { 10, 10.5, 11 }, new double[] { 150, 60, 25 });
        var price = new ClosePriceIndicator(series);
        var volume = new VolumeIndicator(series, 1);

        var indicator = new VolumeProfileKDEIndicator(price, volume, 0, numOf(0.5));

        String json = indicator.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(VolumeProfileKDEIndicator.class);
        var restoredIndicator = (VolumeProfileKDEIndicator) restored;
        assertThat(restoredIndicator.toDescriptor()).isEqualTo(indicator.toDescriptor());
        int index = series.getEndIndex();
        assertThat(restoredIndicator.getValue(index)).isEqualByComparingTo(indicator.getValue(index));
        assertThat(restoredIndicator.getModePrice(index)).isEqualByComparingTo(indicator.getModePrice(index));
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
