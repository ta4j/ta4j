/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.supportresistance.VolumeProfileKDEIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VolumeProfileKDEIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final String PI = "3.1415926535897932384626433832795028841971";

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
    public void shouldComputeGaussianDensityUsingNumMath() {
        BarSeries series = buildSeries(new double[] { 1, 1 }, new double[] { 1, 1 });
        NumFactory factory = series.numFactory();
        List<Num> prices = List.of(factory.numOf("10.125"), factory.numOf("10.375"));
        List<Num> volumes = List.of(factory.numOf("120.5"), factory.numOf("45.25"));

        Indicator<Num> price = new MockIndicator(series, prices);
        Indicator<Num> volume = new MockIndicator(series, volumes);

        Num bandwidth = factory.numOf("0.4");
        VolumeProfileKDEIndicator indicator = new VolumeProfileKDEIndicator(price, volume, 0, bandwidth);

        Num evaluationPrice = factory.numOf("10.25");
        Num expected = gaussianDensity(factory, evaluationPrice, prices, volumes, bandwidth);
        Num actual = indicator.getDensityAtPrice(1, evaluationPrice);

        assertNumEquals(expected, actual);
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

    @Test
    public void unstableBarsIncludeInputWarmupAndLookback() {
        BarSeries series = buildSeries(new double[] { 10, 10.2, 10.4, 10.6, 10.8, 11.0, 11.2, 11.4 },
                new double[] { 100, 90, 80, 70, 60, 50, 40, 30 });
        MockIndicator price = new MockIndicator(series, 3, List.of(numOf(10), numOf(10.2), numOf(10.4), numOf(10.6),
                numOf(10.8), numOf(11.0), numOf(11.2), numOf(11.4)));
        MockIndicator volume = new MockIndicator(series, 1,
                List.of(numOf(100), numOf(90), numOf(80), numOf(70), numOf(60), numOf(50), numOf(40), numOf(30)));
        VolumeProfileKDEIndicator indicator = new VolumeProfileKDEIndicator(price, volume, 4, numOf(0));

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(6);
        assertThat(indicator.getValue(5).isNaN()).isTrue();
        assertThat(indicator.getValue(6).isNaN()).isFalse();
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

    private Num gaussianDensity(NumFactory factory, Num price, List<Num> prices, List<Num> volumes, Num bandwidth) {
        Num twoPi = factory.two().multipliedBy(factory.numOf(PI));
        Num coefficient = factory.one().dividedBy(bandwidth.multipliedBy(twoPi.sqrt()));
        Num negativeHalf = factory.numOf("-0.5");
        Num density = factory.zero();
        for (int i = 0; i < prices.size(); i++) {
            Num diff = price.minus(prices.get(i));
            Num exponent = diff.dividedBy(bandwidth).pow(2).multipliedBy(negativeHalf);
            Num kernel = coefficient.multipliedBy(exponent.exp());
            density = density.plus(volumes.get(i).abs().multipliedBy(kernel));
        }
        return density;
    }
}
