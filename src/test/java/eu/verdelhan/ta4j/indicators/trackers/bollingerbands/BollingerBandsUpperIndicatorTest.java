/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.indicators.trackers.bollingerbands;

import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.StandardDeviationIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class BollingerBandsUpperIndicatorTest {

    private TimeSeries data;

    private int timeFrame;

    private ClosePriceIndicator closePrice;

    private SMAIndicator sma;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
        timeFrame = 3;
        closePrice = new ClosePriceIndicator(data);
        sma = new SMAIndicator(closePrice, timeFrame);
    }

    @Test
    public void testBollingerBandsUpperUsingSMAAndStandardDeviation() throws Exception {

        BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
        StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
        BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

        assertThat(bbuSMA.getValue(0)).isEqualTo(1.0);
        assertThat(bbuSMA.getValue(1)).isEqualTo(2.91, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(2)).isEqualTo(4.82, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(3)).isEqualTo(5.82, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(4)).isEqualTo(4.96, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(5)).isEqualTo(5.29, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(6)).isEqualTo(6.82, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(7)).isEqualTo(5.96, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(8)).isEqualTo(6.82, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(9)).isEqualTo(4.96, TATestsUtils.SHORT_OFFSET);

    }

    @Test
    public void testBollingerBandsUpperShouldWorkJumpingIndexes() {

        BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
        StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
        BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

        assertThat(bbuSMA.getValue(9)).isEqualTo(4.96, TATestsUtils.SHORT_OFFSET);
        assertThat(bbuSMA.getValue(4)).isEqualTo(4.96, TATestsUtils.SHORT_OFFSET);
    }
}
