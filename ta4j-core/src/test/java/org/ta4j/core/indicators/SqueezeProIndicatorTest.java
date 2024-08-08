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
package org.ta4j.core.indicators;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.bollinger.BollingerBandFacade;
import org.ta4j.core.indicators.keltner.KeltnerChannelLowerIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelMiddleIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelUpperIndicator;

import java.time.ZonedDateTime;
import java.util.function.Function;

import static org.junit.Assert.*;

public class SqueezeProIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SqueezeProIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testMobiusSqueezeProIndicatorWithDefaultParameters() {
        BarSeries series = new BaseBarSeries();
        ZonedDateTime startDateTime = ZonedDateTime.now();

        for (int i = 0; i < 50; i++) {
            series.addBar(startDateTime.plusDays(i), i, i, i, i);
        }

        SqueezeProIndicator indicator = new SqueezeProIndicator(series, 20);

        // The indicator should be false for the first 20 bars (unstable period)
        for (int i = 0; i < 20; i++) {
            assertFalse(indicator.getValue(i));
        }

        // Test some values after the unstable period
        // Note: These expected values should be adjusted based on the actual behavior
        // of your indicator
        assertFalse(indicator.getValue(21));
        assertFalse(indicator.getValue(30));
        assertFalse(indicator.getValue(40));
    }

    @Test
    public void testMobiusSqueezeProIndicatorWithCustomParameters() {
        BarSeries series = new BaseBarSeries();
        ZonedDateTime startDateTime = ZonedDateTime.now();

        for (int i = 0; i < 50; i++) {
            series.addBar(startDateTime.plusDays(i), i, i + 10, i - 10, i);
        }

        SqueezeProIndicator indicator = new SqueezeProIndicator(series, 10, 2.5, 1.2, 1.7, 2.2);

        // The indicator should be false for the first 10 bars (unstable period)
        for (int i = 0; i < 10; i++) {
            assertFalse(indicator.getValue(i));
        }

        // Test some values after the unstable period
        // Note: These expected values should be adjusted based on the actual behavior
        // of your indicator
        assertTrue(indicator.getValue(11));
        assertTrue(indicator.getValue(20));
        assertTrue(indicator.getValue(30));
    }

    @Test
    public void testSqueezeCondition() {
        BarSeries series = new BaseBarSeries();
        ZonedDateTime startDateTime = ZonedDateTime.now();

        for (int i = 0; i < 100; i++) {
            series.addBar(startDateTime.plusDays(i), i, 110, 90, 100);
        }

        int barCount = 20;
        double bollingerBandK = 2.0;
        double keltnerShiftFactor = 1.5;

        BollingerBandFacade bollingerBand = new BollingerBandFacade(series, barCount, bollingerBandK);
        KeltnerChannelMiddleIndicator keltnerChannelMidLine = new KeltnerChannelMiddleIndicator(series, barCount);
        ATRIndicator averageTrueRange = new ATRIndicator(series, barCount);
        KeltnerChannelUpperIndicator keltnerChannelUpper = new KeltnerChannelUpperIndicator(keltnerChannelMidLine,
                averageTrueRange, keltnerShiftFactor);
        KeltnerChannelLowerIndicator keltnerChannelLower = new KeltnerChannelLowerIndicator(keltnerChannelMidLine,
                averageTrueRange, keltnerShiftFactor);

        SqueezeProIndicator indicator = new SqueezeProIndicator(series, barCount);

        // Find an index where the squeeze condition is true
        int squeezeIndex = -1;
        for (int i = barCount; i < series.getBarCount(); i++) {
            if (bollingerBand.lower().getValue(i).isGreaterThan(keltnerChannelLower.getValue(i))
                    && bollingerBand.upper().getValue(i).isLessThan(keltnerChannelUpper.getValue(i))) {
                squeezeIndex = i;
                break;
            }
        }

        if (squeezeIndex != -1) {
            assertTrue("Squeeze condition should be true at index " + squeezeIndex, indicator.getValue(squeezeIndex));
        } else {
            fail("No squeeze condition detected in the test data");
        }
    }

    @Test
    public void testChangingSqueezeConditions() {
        BarSeries series = new BaseBarSeries();
        ZonedDateTime startDateTime = ZonedDateTime.now();

        for (int i = 0; i < 100; i++) {
            series.addBar(startDateTime.plusDays(i), i, i + 1, i - 1, i * 2);
        }

        SqueezeProIndicator indicator = new SqueezeProIndicator(series, 20);

        for (int i = 20; i < 38; i++) {
            assertFalse("No squeeze should be detected at index " + i, indicator.getValue(i));
        }
        for (int i = 38; i < 100; i++) {
            assertTrue("Squeeze should be detected at index " + i, indicator.getValue(i));
        }
    }

    @Test
    public void testConsistencyWithUnderlyingIndicators() {
        BarSeries series = new BaseBarSeries();
        ZonedDateTime startDateTime = ZonedDateTime.now();

        for (int i = 0; i < 100; i++) {
            series.addBar(startDateTime.plusDays(i), i, i + 10, i - 10, i);
        }

        int barCount = 20;
        double bollingerBandK = 2.0;
        double keltnerShiftFactorHigh = 1.0;
        double keltnerShiftFactorMid = 1.5;
        double keltnerShiftFactorLow = 2.0;

        SqueezeProIndicator indicator = new SqueezeProIndicator(series, barCount, bollingerBandK,
                keltnerShiftFactorHigh, keltnerShiftFactorMid, keltnerShiftFactorLow);

        BollingerBandFacade bollingerBand = new BollingerBandFacade(series, barCount, bollingerBandK);
        KeltnerChannelMiddleIndicator keltnerChannelMidLine = new KeltnerChannelMiddleIndicator(series, barCount);
        ATRIndicator averageTrueRange = new ATRIndicator(series, barCount);

        KeltnerChannelUpperIndicator keltnerChannelUpperHigh = new KeltnerChannelUpperIndicator(keltnerChannelMidLine,
                averageTrueRange, keltnerShiftFactorHigh);
        KeltnerChannelLowerIndicator keltnerChannelLowerHigh = new KeltnerChannelLowerIndicator(keltnerChannelMidLine,
                averageTrueRange, keltnerShiftFactorHigh);
        KeltnerChannelUpperIndicator keltnerChannelUpperMid = new KeltnerChannelUpperIndicator(keltnerChannelMidLine,
                averageTrueRange, keltnerShiftFactorMid);
        KeltnerChannelLowerIndicator keltnerChannelLowerMid = new KeltnerChannelLowerIndicator(keltnerChannelMidLine,
                averageTrueRange, keltnerShiftFactorMid);
        KeltnerChannelUpperIndicator keltnerChannelUpperLow = new KeltnerChannelUpperIndicator(keltnerChannelMidLine,
                averageTrueRange, keltnerShiftFactorLow);
        KeltnerChannelLowerIndicator keltnerChannelLowerLow = new KeltnerChannelLowerIndicator(keltnerChannelMidLine,
                averageTrueRange, keltnerShiftFactorLow);

        for (int i = barCount; i < series.getBarCount(); i++) {
            boolean expectedSqueeze = (bollingerBand.lower()
                    .getValue(i)
                    .isGreaterThan(keltnerChannelLowerLow.getValue(i))
                    && bollingerBand.upper().getValue(i).isLessThan(keltnerChannelUpperLow.getValue(i)))
                    || (bollingerBand.lower().getValue(i).isGreaterThan(keltnerChannelLowerMid.getValue(i))
                            && bollingerBand.upper().getValue(i).isLessThan(keltnerChannelUpperMid.getValue(i)))
                    || (bollingerBand.lower().getValue(i).isGreaterThan(keltnerChannelLowerHigh.getValue(i))
                            && bollingerBand.upper().getValue(i).isLessThan(keltnerChannelUpperHigh.getValue(i)));

            assertEquals("Indicator value should be consistent with underlying indicators at index " + i,
                    expectedSqueeze, indicator.getValue(i));
        }
    }
}