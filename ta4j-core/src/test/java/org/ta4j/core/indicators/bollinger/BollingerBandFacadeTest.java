/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.bollinger;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BollingerBandFacadeTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public BollingerBandFacadeTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testCreation() {
        final var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
                .build();
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
        final var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
                .build();
        final var closePriceIndicator = new ClosePriceIndicator(data);
        final int barCount = 3;
        final var sma = new SMAIndicator(closePriceIndicator, 3);

        final var middleBB = new BollingerBandsMiddleIndicator(sma);
        final var standardDeviation = new StandardDeviationIndicator(closePriceIndicator, barCount);
        final var lowerBB = new BollingerBandsLowerIndicator(middleBB, standardDeviation);
        final var upperBB = new BollingerBandsUpperIndicator(middleBB, standardDeviation);
        final var pcb = new PercentBIndicator(new ClosePriceIndicator(data), 5, 2);
        final var widthBB = new BollingerBandWidthIndicator(upperBB, middleBB, lowerBB);

        final var bollingerBandFacade = new BollingerBandFacade(data, barCount, 2);
        final var middleBBNumeric = bollingerBandFacade.middle();
        final var lowerBBNumeric = bollingerBandFacade.lower();
        final var upperBBNumeric = bollingerBandFacade.upper();
        final var widthBBNumeric = bollingerBandFacade.bandwidth();

        final var pcbNumeric = new BollingerBandFacade(data, 5, 2).percentB();

        for (int i = data.getBeginIndex(); i <= data.getEndIndex(); i++) {
            assertNumEquals(pcb.getValue(i), pcbNumeric.getValue(i));
            assertNumEquals(lowerBB.getValue(i), lowerBBNumeric.getValue(i));
            assertNumEquals(middleBB.getValue(i), middleBBNumeric.getValue(i));
            assertNumEquals(upperBB.getValue(i), upperBBNumeric.getValue(i));
            assertNumEquals(widthBB.getValue(i), widthBBNumeric.getValue(i));
        }
    }
}
