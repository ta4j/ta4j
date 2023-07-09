/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.ZonedDateTime;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;

public class ChaikinMoneyFlowIndicatorTest {

    @Test
    public void getValue() {

        ZonedDateTime now = ZonedDateTime.now();
        BarSeries series = new BaseBarSeries();
        int sec = 1000;
        series.addBar(now.minusSeconds(sec--), "0", "62.34", "61.37", "62.15", "7849.025");
        series.addBar(now.minusSeconds(sec--), "0", "62.05", "60.69", "60.81", "11692.075");
        series.addBar(now.minusSeconds(sec--), "0", "62.27", "60.10", "60.45", "10575.307");
        series.addBar(now.minusSeconds(sec--), "0", "60.79", "58.61", "59.18", "13059.128");
        series.addBar(now.minusSeconds(sec--), "0", "59.93", "58.71", "59.24", "20733.508");
        series.addBar(now.minusSeconds(sec--), "0", "61.75", "59.86", "60.20", "29630.096");
        series.addBar(now.minusSeconds(sec--), "0", "60.00", "57.97", "58.48", "17705.294");
        series.addBar(now.minusSeconds(sec--), "0", "59.00", "58.02", "58.24", "7259.203");
        series.addBar(now.minusSeconds(sec--), "0", "59.07", "57.48", "58.69", "10474.629");
        series.addBar(now.minusSeconds(sec--), "0", "59.22", "58.30", "58.65", "5203.714");
        series.addBar(now.minusSeconds(sec--), "0", "58.75", "57.83", "58.47", "3422.865");
        series.addBar(now.minusSeconds(sec--), "0", "58.65", "57.86", "58.02", "3962.150");
        series.addBar(now.minusSeconds(sec--), "0", "58.47", "57.91", "58.17", "4095.905");
        series.addBar(now.minusSeconds(sec--), "0", "58.25", "57.83", "58.07", "3766.006");
        series.addBar(now.minusSeconds(sec--), "0", "58.35", "57.53", "58.13", "4239.335");
        series.addBar(now.minusSeconds(sec--), "0", "59.86", "58.58", "58.94", "8039.979");
        series.addBar(now.minusSeconds(sec--), "0", "59.53", "58.30", "59.10", "6956.717");
        series.addBar(now.minusSeconds(sec--), "0", "62.10", "58.53", "61.92", "18171.552");
        series.addBar(now.minusSeconds(sec--), "0", "62.16", "59.80", "61.37", "22225.894");

        series.addBar(now.minusSeconds(sec--), "0", "62.67", "60.93", "61.68", "14613.509");
        series.addBar(now.minusSeconds(sec--), "0", "62.38", "60.15", "62.09", "12319.763");
        series.addBar(now.minusSeconds(sec--), "0", "63.73", "62.26", "62.89", "15007.690");
        series.addBar(now.minusSeconds(sec--), "0", "63.85", "63.00", "63.53", "8879.667");
        series.addBar(now.minusSeconds(sec--), "0", "66.15", "63.58", "64.01", "22693.812");
        series.addBar(now.minusSeconds(sec--), "0", "65.34", "64.07", "64.77", "10191.814");
        series.addBar(now.minusSeconds(sec--), "0", "66.48", "65.20", "65.22", "10074.152");
        series.addBar(now.minusSeconds(sec--), "0", "65.23", "63.21", "63.28", "9411.620");
        series.addBar(now.minusSeconds(sec--), "0", "63.40", "61.88", "62.40", "10391.690");
        series.addBar(now.minusSeconds(sec--), "0", "63.18", "61.11", "61.55", "8926.512");
        series.addBar(now.minusSeconds(sec--), "0", "62.70", "61.25", "62.69", "7459.575");

        ChaikinMoneyFlowIndicator cmf = new ChaikinMoneyFlowIndicator(series, 20);

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
