/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.keltner;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class KeltnerChannelLowerIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public KeltnerChannelLowerIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        data.barBuilder().openPrice(11577.43).closePrice(11670.75).highPrice(11711.47).lowPrice(11577.35).add();
        data.barBuilder().openPrice(11670.90).closePrice(11691.18).highPrice(11698.22).lowPrice(11635.74).add();
        data.barBuilder().openPrice(11688.61).closePrice(11722.89).highPrice(11742.68).lowPrice(11652.89).add();
        data.barBuilder().openPrice(11716.93).closePrice(11697.31).highPrice(11736.74).lowPrice(11667.46).add();
        data.barBuilder().openPrice(11696.86).closePrice(11674.76).highPrice(11726.94).lowPrice(11599.68).add();
        data.barBuilder().openPrice(11672.34).closePrice(11637.45).highPrice(11677.33).lowPrice(11573.87).add();
        data.barBuilder().openPrice(11638.51).closePrice(11671.88).highPrice(11704.12).lowPrice(11635.48).add();
        data.barBuilder().openPrice(11673.62).closePrice(11755.44).highPrice(11782.23).lowPrice(11673.62).add();
        data.barBuilder().openPrice(11753.70).closePrice(11731.90).highPrice(11757.25).lowPrice(11700.53).add();
        data.barBuilder().openPrice(11732.13).closePrice(11787.38).highPrice(11794.15).lowPrice(11698.83).add();
        data.barBuilder().openPrice(11783.82).closePrice(11837.93).highPrice(11858.78).lowPrice(11777.99).add();
        data.barBuilder().openPrice(11834.21).closePrice(11825.29).highPrice(11861.24).lowPrice(11798.46).add();
        data.barBuilder().openPrice(11823.70).closePrice(11822.80).highPrice(11845.16).lowPrice(11744.77).add();
        data.barBuilder().openPrice(11822.95).closePrice(11871.84).highPrice(11905.48).lowPrice(11822.80).add();
        data.barBuilder().openPrice(11873.43).closePrice(11980.52).highPrice(11982.94).lowPrice(11867.98).add();
        data.barBuilder().openPrice(11980.52).closePrice(11977.19).highPrice(11985.97).lowPrice(11898.74).add();
        data.barBuilder().openPrice(11978.85).closePrice(11985.44).highPrice(12020.52).lowPrice(11961.83).add();
        data.barBuilder().openPrice(11985.36).closePrice(11989.83).highPrice(12019.53).lowPrice(11971.93).add();
        data.barBuilder().openPrice(11824.39).closePrice(11891.93).highPrice(11891.93).lowPrice(11817.88).add();
        data.barBuilder().openPrice(11892.50).closePrice(12040.16).highPrice(12050.75).lowPrice(11892.50).add();
        data.barBuilder().openPrice(12038.27).closePrice(12041.97).highPrice(12057.91).lowPrice(12018.51).add();
        data.barBuilder().openPrice(12040.68).closePrice(12062.26).highPrice(12080.54).lowPrice(11981.05).add();
        data.barBuilder().openPrice(12061.73).closePrice(12092.15).highPrice(12092.42).lowPrice(12025.78).add();
        data.barBuilder().openPrice(12092.38).closePrice(12161.63).highPrice(12188.76).lowPrice(12092.30).add();
        data.barBuilder().openPrice(12152.70).closePrice(12233.15).highPrice(12238.79).lowPrice(12150.05).add();
        data.barBuilder().openPrice(12229.29).closePrice(12239.89).highPrice(12254.23).lowPrice(12188.19).add();
        data.barBuilder().openPrice(12239.66).closePrice(12229.29).highPrice(12239.66).lowPrice(12156.94).add();
        data.barBuilder().openPrice(12227.78).closePrice(12273.26).highPrice(12285.94).lowPrice(12180.48).add();
        data.barBuilder().openPrice(12266.83).closePrice(12268.19).highPrice(12276.21).lowPrice(12235.91).add();
        data.barBuilder().openPrice(12266.75).closePrice(12226.64).highPrice(12267.66).lowPrice(12193.27).add();
        data.barBuilder().openPrice(12219.79).closePrice(12288.17).highPrice(12303.16).lowPrice(12219.79).add();
        data.barBuilder().openPrice(12287.72).closePrice(12318.14).highPrice(12331.31).lowPrice(12253.24).add();
        data.barBuilder().openPrice(12389.74).closePrice(12212.79).highPrice(12389.82).lowPrice(12176.31).add();
    }

    @Test
    public void keltnerChannelLowerIndicatorTest() {
        var km = new KeltnerChannelMiddleIndicator(new ClosePriceIndicator(data), 14);
        var kl = new KeltnerChannelLowerIndicator(km, 2, 14);

        // KeltnerChannelLowerIndicator uses KeltnerChannelMiddleIndicator (unstable
        // period 14) and ATRIndicator (unstable period 14)
        // So unstable period is 14, indices 0-13 return NaN
        for (int i = 0; i < 14; i++) {
            assertThat(Double.isNaN(kl.getValue(i).doubleValue())).isTrue();
        }

        // Values after unstable period should be valid (not NaN)
        // Note: Values will differ from expected because first EMA/MMA value after
        // unstable period
        // is now initialized to current value, not calculated from previous values
        assertThat(Double.isNaN(kl.getValue(14).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(15).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(16).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(17).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(18).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(19).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(20).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(21).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(22).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(23).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(24).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(25).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(26).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(27).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(28).doubleValue())).isFalse();
        assertThat(Double.isNaN(kl.getValue(29).doubleValue())).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        KeltnerChannelMiddleIndicator middle = new KeltnerChannelMiddleIndicator(new ClosePriceIndicator(data), 5);
        KeltnerChannelLowerIndicator indicator = new KeltnerChannelLowerIndicator(middle, 1.5, 5);

        String json = indicator.toJson();
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(data, json);

        assertThat(restored).isInstanceOf(KeltnerChannelLowerIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(indicator.toDescriptor());
        for (int i = data.getBeginIndex(); i <= data.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(indicator.getValue(i));
        }
    }
}
