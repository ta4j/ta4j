/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class RSIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;
    private ExternalIndicatorTest xls;
    // private ExternalIndicatorTest sql;

    public RSIIndicatorTest(Function<Number, Num> numFunction) {
        super((data, params) -> new RSIIndicator((Indicator<Num>) data, (int) params[0]), numFunction);
        xls = new XLSIndicatorTest(this.getClass(), "RSI.xls", 10, numFunction);
        // sql = new SQLIndicatorTest(this.getClass(), "RSI.db", username, pass, table,
        // column);
    }

    @Before
    public void setUp() throws Exception {
        data = new MockBarSeries(numFunction, 50.45, 50.30, 50.20, 50.15, 50.05, 50.06, 50.10, 50.08, 50.03, 50.07,
                50.01, 50.14, 50.22, 50.43, 50.50, 50.56, 50.52, 50.70, 50.55, 50.62, 50.90, 50.82, 50.86, 51.20, 51.30,
                51.10);
    }

    @Test
    public void firstValueShouldBeZero() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 14);
        assertEquals(data.numOf(0), indicator.getValue(0));
    }

    @Test
    public void hundredIfNoLoss() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertEquals(data.numOf(100), indicator.getValue(14));
        assertEquals(data.numOf(100), indicator.getValue(15));
    }

    @Test
    public void zeroIfNoGain() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        assertEquals(data.numOf(0), indicator.getValue(1));
        assertEquals(data.numOf(0), indicator.getValue(2));
    }

    @Test
    public void usingBarCount14UsingClosePrice() throws Exception {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 14);
        assertEquals(68.4746, indicator.getValue(15).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(64.7836, indicator.getValue(16).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(72.0776, indicator.getValue(17).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(60.7800, indicator.getValue(18).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(63.6439, indicator.getValue(19).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(72.3433, indicator.getValue(20).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(67.3822, indicator.getValue(21).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(68.5438, indicator.getValue(22).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(76.2770, indicator.getValue(23).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(77.9908, indicator.getValue(24).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(67.4895, indicator.getValue(25).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void xlsTest() throws Exception {
        Indicator<Num> xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Num> indicator;

        indicator = getIndicator(xlsClose, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator);
        assertEquals(100.0, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsClose, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator);
        assertEquals(67.0453, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsClose, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator);
        assertEquals(52.5876, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void onlineExampleTest() throws Exception {
        // from
        // http://cns.bu.edu/~gsc/CN710/fincast/Technical%20_indicators/Relative%20Strength%20Index%20(RSI).htm
        // which uses a different calculation of RSI than ta4j
        BarSeries series = new MockBarSeries(numFunction, 46.1250, 47.1250, 46.4375, 46.9375, 44.9375, 44.2500, 44.6250,
                45.7500, 47.8125, 47.5625, 47.0000, 44.5625, 46.3125, 47.6875, 46.6875, 45.6875, 43.0625, 43.5625,
                44.8750, 43.6875);
        // ta4j RSI uses MMA for average gain and loss
        // then uses simple division of the two for RS
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(series), 14);
        Indicator<Num> close = new ClosePriceIndicator(series);
        Indicator<Num> gain = new GainIndicator(close);
        Indicator<Num> loss = new LossIndicator(close);
        // this site uses SMA for average gain and loss
        // then uses ratio of MMAs for RS (except for first calculation)
        Indicator<Num> avgGain = new SMAIndicator(gain, 14);
        Indicator<Num> avgLoss = new SMAIndicator(loss, 14);

        // first online calculation is simple division
        double onlineRs = avgGain.getValue(14).dividedBy(avgLoss.getValue(14)).doubleValue();
        assertEquals(0.5848, avgGain.getValue(14).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(0.5446, avgLoss.getValue(14).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(1.0738, onlineRs, TestUtils.GENERAL_OFFSET);
        double onlineRsi = 100d - (100d / (1d + onlineRs));
        // difference in RSI values:
        assertEquals(51.779, onlineRsi, 0.001);
        assertEquals(52.1304, indicator.getValue(14).doubleValue(), TestUtils.GENERAL_OFFSET);

        // strange, online average gain and loss is not a simple moving average!
        // but they only use them for the first RS calculation
        // assertEquals(0.5430, avgGain.getValue(15).doubleValue(),
        // TATestsUtils.GENERAL_OFFSET);
        // assertEquals(0.5772, avgLoss.getValue(15).doubleValue(),
        // TATestsUtils.GENERAL_OFFSET);
        // second online calculation uses MMAs
        // MMA of average gain
        double dividend = avgGain.getValue(14)
                .multipliedBy(series.numOf(13))
                .plus(gain.getValue(15))
                .dividedBy(series.numOf(14))
                .doubleValue();
        // MMA of average loss
        double divisor = avgLoss.getValue(14)
                .multipliedBy(series.numOf(13))
                .plus(loss.getValue(15))
                .dividedBy(series.numOf(14))
                .doubleValue();
        onlineRs = dividend / divisor;
        assertEquals(0.9409, onlineRs, TestUtils.GENERAL_OFFSET);
        onlineRsi = 100d - (100d / (1d + onlineRs));
        // difference in RSI values:
        assertEquals(48.477, onlineRsi, 0.001);
        assertEquals(47.3710, indicator.getValue(15).doubleValue(), TestUtils.GENERAL_OFFSET);
    }
}
