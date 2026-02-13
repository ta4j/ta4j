/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VWAPDerivedIndicatorsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;
    private ClosePriceIndicator closePrice;
    private VolumeIndicator volume;
    private VWAPIndicator vwap;

    /**
     * Creates a new VWAPDerivedIndicatorsTest instance.
     */
    public VWAPDerivedIndicatorsTest(NumFactory numFactory) {
        super(numFactory);
    }

    /**
     * Initializes the test fixtures used by these scenarios.
     */
    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(10).openPrice(10).highPrice(10).lowPrice(10).volume(1).add();
        series.barBuilder().closePrice(12).openPrice(12).highPrice(12).lowPrice(12).volume(2).add();
        series.barBuilder().closePrice(11).openPrice(11).highPrice(11).lowPrice(11).volume(3).add();
        series.barBuilder().closePrice(14).openPrice(14).highPrice(14).lowPrice(14).volume(4).add();
        closePrice = new ClosePriceIndicator(series);
        volume = new VolumeIndicator(series);
        vwap = new VWAPIndicator(closePrice, volume, 3);
    }

    /**
     * Implements vwap standard deviation and deviation.
     */
    @Test
    public void vwapStandardDeviationAndDeviation() {
        var std = new VWAPStandardDeviationIndicator(vwap);
        var deviation = new VWAPDeviationIndicator(closePrice, vwap);

        assertThat(deviation.getValue(1).isNaN()).isTrue();

        assertNumEquals(0.6872, std.getValue(2));
        assertNumEquals(1.3426, std.getValue(3));

        assertNumEquals(-0.1667, deviation.getValue(2));
        assertNumEquals(1.4444, deviation.getValue(3));

        assertThat(std.getWindowStartIndex(3)).isEqualTo(1);
    }

    /**
     * Implements vwap zscore.
     */
    @Test
    public void vwapZScore() {
        var std = new VWAPStandardDeviationIndicator(vwap);
        var deviation = new VWAPDeviationIndicator(closePrice, vwap);
        var zScore = new VWAPZScoreIndicator(deviation, std);

        assertNumEquals(1.0759, zScore.getValue(3));

        var flatSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        flatSeries.barBuilder().closePrice(5).openPrice(5).highPrice(5).lowPrice(5).volume(10).add();
        flatSeries.barBuilder().closePrice(5).openPrice(5).highPrice(5).lowPrice(5).volume(8).add();
        var flatClose = new ClosePriceIndicator(flatSeries);
        var flatVolume = new VolumeIndicator(flatSeries);
        var flatVwap = new VWAPIndicator(flatClose, flatVolume, 2);
        var flatStd = new VWAPStandardDeviationIndicator(flatVwap);
        var flatDeviation = new VWAPDeviationIndicator(flatClose, flatVwap);
        var flatZScore = new VWAPZScoreIndicator(flatDeviation, flatStd);

        assertThat(flatZScore.getValue(1).isNaN()).isTrue();
    }

    /**
     * Implements vwap bands.
     */
    @Test
    public void vwapBands() {
        var std = new VWAPStandardDeviationIndicator(vwap);
        var upper = new VWAPBandIndicator(vwap, std, 2, VWAPBandIndicator.BandType.UPPER);
        var lower = new VWAPBandIndicator(vwap, std, 2, VWAPBandIndicator.BandType.LOWER);
        var upperConvenience = new VWAPBandIndicator(vwap, 2, VWAPBandIndicator.BandType.UPPER);

        assertNumEquals(12.5411, upper.getValue(2));
        assertNumEquals(9.7923, lower.getValue(2));
        assertNumEquals(15.2407, upper.getValue(3));
        assertNumEquals(9.8704, lower.getValue(3));
        assertNumEquals(12.5411, upperConvenience.getValue(2));

        var invalidSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        invalidSeries.barBuilder().closePrice(10).openPrice(10).highPrice(10).lowPrice(10).volume(1).add();
        invalidSeries.barBuilder().closePrice(11).openPrice(11).highPrice(11).lowPrice(11).volume(-1).add();
        var invalidClose = new ClosePriceIndicator(invalidSeries);
        var invalidVolume = new VolumeIndicator(invalidSeries);
        var invalidVwap = new VWAPIndicator(invalidClose, invalidVolume, 2);
        var invalidStd = new VWAPStandardDeviationIndicator(invalidVwap);
        var invalidUpper = new VWAPBandIndicator(invalidVwap, invalidStd, 1, VWAPBandIndicator.BandType.UPPER);

        assertThat(invalidUpper.getValue(1).isNaN()).isTrue();
    }

    /**
     * Verifies that round trip serialize and deserialize.
     */
    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        var std = new VWAPStandardDeviationIndicator(vwap);
        var deviation = new VWAPDeviationIndicator(closePrice, vwap);
        var zScore = new VWAPZScoreIndicator(deviation, std);
        var upper = new VWAPBandIndicator(vwap, std, 2, VWAPBandIndicator.BandType.UPPER);

        assertRoundTrip(std, 3);
        assertRoundTrip(deviation, 3);
        assertRoundTrip(zScore, 3);
        assertRoundTrip(upper, 3);
    }

    /**
     * Implements z score unstable bars track source indicators.
     */
    @Test
    public void zScoreUnstableBarsTrackSourceIndicators() {
        MockIndicator deviation = new MockIndicator(series, 4, numOf(0), numOf(0.5), numOf(1.0), numOf(1.5));
        MockIndicator standardDeviation = new MockIndicator(series, 2, numOf(1), numOf(1), numOf(1), numOf(1));
        VWAPZScoreIndicator zScore = new VWAPZScoreIndicator(deviation, standardDeviation);

        assertThat(zScore.getCountOfUnstableBars()).isEqualTo(4);
    }

    /**
     * Implements assert round trip.
     */
    @SuppressWarnings("unchecked")
    private void assertRoundTrip(Indicator<Num> indicator, int index) {
        String json = indicator.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(indicator.getClass());
        assertThat(restored.toDescriptor()).isEqualTo(indicator.toDescriptor());
        Indicator<Num> restoredIndicator = (Indicator<Num>) restored;
        assertThat(restoredIndicator.getValue(index)).isEqualByComparingTo(indicator.getValue(index));
    }
}
