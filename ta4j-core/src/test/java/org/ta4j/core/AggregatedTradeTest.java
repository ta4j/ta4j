/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AggregatedTradeTest extends AbstractIndicatorTest<BarSeries, Num> {

    public AggregatedTradeTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void computesWeightedAveragePriceAndTotalAmount() {
        TradeFill firstFill = new TradeFill(2, numFactory.hundred(), numFactory.two());
        TradeFill secondFill = new TradeFill(3, numFactory.numOf(110), numFactory.one());
        AggregatedTrade trade = new AggregatedTrade(TradeType.BUY, List.of(firstFill, secondFill));

        assertEquals(TradeType.BUY, trade.getType());
        assertEquals(2, trade.getIndex());
        assertNumEquals(numFactory.three(), trade.getAmount());
        assertNumEquals(numFactory.numOf(103.3333333333), trade.getPricePerAsset(), 0.0001);
        assertEquals(List.of(firstFill, secondFill), trade.getFills());
    }

    @Test
    public void rejectsEmptyFillCollections() {
        assertThrows(IllegalArgumentException.class, () -> new AggregatedTrade(TradeType.BUY, List.of()));
    }

    @Test
    public void rejectsNanFillPrice() {
        TradeFill fill = new TradeFill(1, NaN.NaN, numFactory.one());
        assertThrows(IllegalArgumentException.class, () -> new AggregatedTrade(TradeType.BUY, List.of(fill)));
    }

    @Test
    public void rejectsNonPositiveFillAmount() {
        TradeFill zeroAmountFill = new TradeFill(1, numFactory.hundred(), numFactory.zero());
        TradeFill negativeAmountFill = new TradeFill(1, numFactory.hundred(), numFactory.minusOne());

        assertThrows(IllegalArgumentException.class, () -> new AggregatedTrade(TradeType.BUY, List.of(zeroAmountFill)));
        assertThrows(IllegalArgumentException.class,
                () -> new AggregatedTrade(TradeType.BUY, List.of(negativeAmountFill)));
    }
}
