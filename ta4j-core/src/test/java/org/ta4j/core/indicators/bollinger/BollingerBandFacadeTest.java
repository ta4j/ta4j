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
package org.ta4j.core.indicators.bollinger;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.GENERAL_OFFSET;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class BollingerBandFacadeTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public BollingerBandFacadeTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testCreation() {
        final BarSeries data = new MockBarSeries(numFunction, 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
        final int barCount = 3;

        final BollingerBandFacade bollingerBandFacade = new BollingerBandFacade(data, barCount, 2);

        assertEquals(data, bollingerBandFacade.bandwidth().getBarSeries());
        assertEquals(data, bollingerBandFacade.middle().getBarSeries());

        final BollingerBandFacade bollingerBandFacadeOfIndicator = new BollingerBandFacade(new OpenPriceIndicator(data),
                barCount, 2);

        assertEquals(data, bollingerBandFacadeOfIndicator.lower().getBarSeries());
        assertEquals(data, bollingerBandFacadeOfIndicator.upper().getBarSeries());
    }

    @Test
    public void testNumericFacadesSameAsDefaultIndicators() {
        final BarSeries data = new MockBarSeries(numFunction, 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
        final ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(data);
        final int barCount = 3;
        final Indicator<Num> sma = new SMAIndicator(closePriceIndicator, 3);

        final BollingerBandsMiddleIndicator middleBB = new BollingerBandsMiddleIndicator(sma);
        final StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePriceIndicator,
                barCount);
        final BollingerBandsLowerIndicator lowerBB = new BollingerBandsLowerIndicator(middleBB, standardDeviation);
        final BollingerBandsUpperIndicator upperBB = new BollingerBandsUpperIndicator(middleBB, standardDeviation);
        final PercentBIndicator pcb = new PercentBIndicator(new ClosePriceIndicator(data), 5, 2);
        final BollingerBandWidthIndicator widthBB = new BollingerBandWidthIndicator(upperBB, middleBB, lowerBB);

        final BollingerBandFacade bollingerBandFacade = new BollingerBandFacade(data, barCount, 2);
        final NumericIndicator middleBBNumeric = bollingerBandFacade.middle();
        final NumericIndicator lowerBBNumeric = bollingerBandFacade.lower();
        final NumericIndicator upperBBNumeric = bollingerBandFacade.upper();
        final NumericIndicator widthBBNumeric = bollingerBandFacade.bandwidth();

        final NumericIndicator pcbNumeric = new BollingerBandFacade(data, 5, 2).percentB();

        for (int i = data.getBeginIndex(); i <= data.getEndIndex(); i++) {
            assertNumEquals(pcb.getValue(i), pcbNumeric.getValue(i));
            assertNumEquals(lowerBB.getValue(i), lowerBBNumeric.getValue(i));
            assertNumEquals(middleBB.getValue(i), middleBBNumeric.getValue(i));
            assertNumEquals(upperBB.getValue(i), upperBBNumeric.getValue(i));
            assertNumEquals(widthBB.getValue(i), widthBBNumeric.getValue(i));
        }
    }

    @Test
    public void testVwapBasedFacadeIsSameAsDefaultVwapIndicator() {
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(1, 1, 1, 1, 1, numFunction));
        bars.add(new MockBar(2, 2, 2, 2, 1, numFunction));
        bars.add(new MockBar(3, 3, 3, 3, 1, numFunction));
        bars.add(new MockBar(4, 4, 4, 4, 1, numFunction));
        bars.add(new MockBar(5, 5, 5, 5, 1, numFunction));
        bars.add(new MockBar(4, 4, 4, 4, 1, numFunction));
        bars.add(new MockBar(3, 3, 3, 3, 1, numFunction));
        bars.add(new MockBar(3, 3, 3, 3, 1, numFunction));
        bars.add(new MockBar(3, 3, 3, 3, 1, numFunction));
        bars.add(new MockBar(4, 4, 4, 4, 1, numFunction));
        bars.add(new MockBar(3, 3, 3, 3, 1, numFunction));
        bars.add(new MockBar(2, 2, 2, 2, 1, numFunction));

        BarSeries series = new MockBarSeries(bars);
        int barCount = 3;

        VWAPIndicator vwapIndicator = new VWAPIndicator(series, barCount);

        BollingerBandsMiddleIndicator middleBB = new BollingerBandsMiddleIndicator(vwapIndicator);
        StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(vwapIndicator,
                barCount);
        BollingerBandsLowerIndicator lowerBB = new BollingerBandsLowerIndicator(middleBB, standardDeviation);
        BollingerBandsUpperIndicator upperBB = new BollingerBandsUpperIndicator(middleBB, standardDeviation);
        PercentBIndicator pcb = new PercentBIndicator(new ClosePriceIndicator(series), barCount, 2);
        BollingerBandWidthIndicator widthBB = new BollingerBandWidthIndicator(upperBB, middleBB, lowerBB);

        BollingerBandFacade bollingerBandFacade = new BollingerBandFacade(vwapIndicator, 3, 2);
        NumericIndicator middleBBNumeric = bollingerBandFacade.middle();
        NumericIndicator lowerBBNumeric = bollingerBandFacade.lower();
        NumericIndicator upperBBNumeric = bollingerBandFacade.upper();
        NumericIndicator widthBBNumeric = bollingerBandFacade.bandwidth();

        NumericIndicator pcbNumeric = new BollingerBandFacade(series, 3, 2).percentB();

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(pcb.getValue(i), pcbNumeric.getValue(i));
            assertNumEquals(lowerBB.getValue(i), lowerBBNumeric.getValue(i));
            assertNumEquals(middleBB.getValue(i), middleBBNumeric.getValue(i));
            assertNumEquals(upperBB.getValue(i), upperBBNumeric.getValue(i));
            assertNumEquals(widthBB.getValue(i), widthBBNumeric.getValue(i));
        }
    }
}
