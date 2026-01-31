/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ChaikinOscillatorIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ChaikinOscillatorIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValue() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder()
                .openPrice(12.915)
                .closePrice(13.600)
                .highPrice(12.890)
                .lowPrice(13.550)
                .volume(264266)
                .add();
        series.barBuilder()
                .openPrice(13.550)
                .closePrice(13.770)
                .highPrice(13.310)
                .lowPrice(13.505)
                .volume(305427)
                .add();
        series.barBuilder()
                .openPrice(13.510)
                .closePrice(13.590)
                .highPrice(13.425)
                .lowPrice(13.490)
                .volume(104077)
                .add();
        series.barBuilder()
                .openPrice(13.515)
                .closePrice(13.545)
                .highPrice(13.400)
                .lowPrice(13.480)
                .volume(136135)
                .add();
        series.barBuilder().openPrice(13.490).closePrice(13.495).highPrice(13.310).lowPrice(13.345).volume(92090).add();
        series.barBuilder().openPrice(13.350).closePrice(13.490).highPrice(13.325).lowPrice(13.420).volume(80948).add();
        series.barBuilder().openPrice(13.415).closePrice(13.460).highPrice(13.290).lowPrice(13.300).volume(82983).add();
        series.barBuilder()
                .openPrice(13.320)
                .closePrice(13.320)
                .highPrice(13.090)
                .lowPrice(13.130)
                .volume(126918)
                .add();
        series.barBuilder().openPrice(13.145).closePrice(13.225).highPrice(13.090).lowPrice(13.150).volume(68560).add();
        series.barBuilder().openPrice(13.150).closePrice(13.250).highPrice(13.110).lowPrice(13.245).volume(41178).add();
        series.barBuilder().openPrice(13.245).closePrice(13.250).highPrice(13.120).lowPrice(13.210).volume(63606).add();
        series.barBuilder().openPrice(13.210).closePrice(13.275).highPrice(13.185).lowPrice(13.275).volume(34402).add();

        var co = new ChaikinOscillatorIndicator(series);

        // Chaikin Oscillator uses two EMA indicators (short=3, long=10)
        // The long EMA defines the unstable period (10 bars)
        for (int i = 0; i < 10; i++) {
            assertThat(Num.isNaNOrNull(co.getValue(i))).isTrue();
        }

        // After the unstable period, values should be defined (non-NaN)
        for (int i = 10; i < series.getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(co.getValue(i))).isFalse();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        ChaikinOscillatorIndicator indicator = new ChaikinOscillatorIndicator(series);

        String json = indicator.toJson();
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(ChaikinOscillatorIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(indicator.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(indicator.getValue(i));
        }
    }
}
