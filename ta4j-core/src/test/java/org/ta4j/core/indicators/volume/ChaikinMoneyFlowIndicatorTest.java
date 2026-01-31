/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class ChaikinMoneyFlowIndicatorTest {

    @Test
    public void getValue() {

        BarSeries series = new MockBarSeriesBuilder().build();
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
        series.barBuilder().highPrice(62.38).lowPrice(60.15).closePrice(62.09).volume(12319.763).add();
        series.barBuilder().highPrice(63.73).lowPrice(62.26).closePrice(62.89).volume(15007.690).add();
        series.barBuilder().highPrice(63.85).lowPrice(63.00).closePrice(63.53).volume(8879.667).add();
        series.barBuilder().highPrice(66.15).lowPrice(63.58).closePrice(64.01).volume(22693.812).add();
        series.barBuilder().openPrice(0).highPrice(65.34).lowPrice(64.07).closePrice(64.77).volume(10191.814).add();
        series.barBuilder().highPrice(66.48).lowPrice(65.20).closePrice(65.22).volume(10074.152).add();
        series.barBuilder().highPrice(65.23).lowPrice(63.21).closePrice(63.28).volume(9411.620).add();
        series.barBuilder().highPrice(63.40).lowPrice(61.88).closePrice(62.40).volume(10391.690).add();
        series.barBuilder().highPrice(63.18).lowPrice(61.11).closePrice(61.55).volume(8926.512).add();
        series.barBuilder().highPrice(62.70).lowPrice(61.25).closePrice(62.69).volume(7459.575).add();

        var cmf = new ChaikinMoneyFlowIndicator(series, 20);

        assertNumEquals(0.6082, cmf.getValue(0));
        assertNumEquals(-0.2484, cmf.getValue(1));
        assertNumEquals(-0.1211, cmf.getValue(19));
        assertNumEquals(-0.0997, cmf.getValue(20));
        assertNumEquals(-0.0659, cmf.getValue(21));
        assertNumEquals(-0.0257, cmf.getValue(22));
        assertNumEquals(-0.0617, cmf.getValue(23));
        assertNumEquals(-0.0481, cmf.getValue(24));
        assertNumEquals(-0.0086, cmf.getValue(25));
        assertNumEquals(-0.0087, cmf.getValue(26));
        assertNumEquals(-0.005, cmf.getValue(27));
        assertNumEquals(-0.0574, cmf.getValue(28));
        assertNumEquals(-0.0148, cmf.getValue(29));
    }
}
