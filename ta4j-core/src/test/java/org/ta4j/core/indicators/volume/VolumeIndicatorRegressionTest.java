/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Regression tests for VolumeIndicator partial-sums optimization. Verifies
 * correct output when used with large windows and in composite scenarios.
 */
public class VolumeIndicatorRegressionTest extends AbstractIndicatorTest<org.ta4j.core.Indicator<Num>, Num> {

    public VolumeIndicatorRegressionTest(NumFactory numFactory) {
        super(numFactory);
    }

    /**
     * Regression: VolumeIndicator with barCount 20 produces correct rolling sum
     * when used by ChaikinMoneyFlowIndicator (integration scenario).
     */
    @Test
    public void volumeIndicatorWithLargeWindowInChaikinMoneyFlow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().highPrice(62.34).lowPrice(61.37).closePrice(62.15).volume(7849.025).add();
        series.barBuilder().highPrice(62.05).lowPrice(60.69).closePrice(60.81).volume(11692.075).add();
        series.barBuilder().highPrice(62.27).lowPrice(60.10).closePrice(60.45).volume(10575.307).add();
        series.barBuilder().highPrice(60.79).lowPrice(58.61).closePrice(59.18).volume(13059.128).add();
        series.barBuilder().highPrice(59.93).lowPrice(58.71).closePrice(59.24).volume(20733.508).add();
        series.barBuilder().highPrice(61.75).lowPrice(59.86).closePrice(60.20).volume(29630.096).add();
        series.barBuilder().highPrice(60.00).lowPrice(57.97).closePrice(58.48).volume(17705.294).add();
        series.barBuilder().highPrice(59.00).lowPrice(58.02).closePrice(58.24).volume(7259.203).add();
        series.barBuilder().highPrice(59.07).lowPrice(57.48).closePrice(58.69).volume(10474.629).add();
        series.barBuilder().highPrice(59.22).lowPrice(58.30).closePrice(58.65).volume(5203.714).add();
        series.barBuilder().highPrice(58.75).lowPrice(57.83).closePrice(58.47).volume(3422.865).add();
        series.barBuilder().highPrice(58.65).lowPrice(57.86).closePrice(58.02).volume(3962.150).add();
        series.barBuilder().highPrice(58.47).lowPrice(57.91).closePrice(58.17).volume(4095.905).add();
        series.barBuilder().highPrice(58.25).lowPrice(57.83).closePrice(58.07).volume(3766.006).add();
        series.barBuilder().highPrice(58.35).lowPrice(57.53).closePrice(58.13).volume(4239.335).add();
        series.barBuilder().highPrice(59.86).lowPrice(58.58).closePrice(58.94).volume(8039.979).add();
        series.barBuilder().openPrice(0).highPrice(59.53).lowPrice(58.30).closePrice(59.10).volume(6956.717).add();
        series.barBuilder().highPrice(62.10).lowPrice(58.53).closePrice(61.92).volume(18171.552).add();
        series.barBuilder().highPrice(62.16).lowPrice(59.80).closePrice(61.37).volume(22225.894).add();
        series.barBuilder().highPrice(62.67).lowPrice(60.93).closePrice(61.68).volume(14613.509).add();

        VolumeIndicator volume = new VolumeIndicator(series, 20);
        assertNumEquals(223675.891, volume.getValue(19));

        ChaikinMoneyFlowIndicator cmf = new ChaikinMoneyFlowIndicator(series, 20);
        assertNumEquals(-0.1211, cmf.getValue(19));
    }
}
