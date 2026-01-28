/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.analysis;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CumulativePnLTest extends AbstractIndicatorTest<org.ta4j.core.Indicator<Num>, Num> {

    public CumulativePnLTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void sizeWithoutTrades() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var pnl = new CumulativePnL(series, new BaseTradingRecord());

        assertEquals(5, pnl.getSize());
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(4));
    }

    @Test
    public void longAndShortPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 95, 90).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series));

        var pnl = new CumulativePnL(series, record);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(5, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
        assertNumEquals(10, pnl.getValue(3));
    }

    @Test
    public void openPositionUsesFinalPrice() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 102).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(5, pnl.getValue(1));
        assertNumEquals(2, pnl.getValue(2));
    }

    @Test
    public void realizedModeUsesExitOnly() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.REALIZED);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
    }

    @Test
    public void realizedModeIgnoresOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 102).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.REALIZED);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(0, pnl.getValue(2));
    }

    @Test
    public void markToMarketCanIgnoreOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 102).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(0, pnl.getValue(2));
    }

    @Test
    public void realizedModeIgnoresOpenPositionEvenWithMarkToMarketHandling() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 102).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(0, pnl.getValue(2));
    }

    @Test
    public void markToMarketRespectsFinalIndexForOpenPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record, 1, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(10, pnl.getValue(1));
    }

    @Test
    public void openShortPositionMarkToMarketAndRealized() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 90).build();
        var record = new BaseTradingRecord(Trade.sellAt(0, series));

        var markToMarket = new CumulativePnL(series, record);
        assertNumEquals(0, markToMarket.getValue(0));
        assertNumEquals(5, markToMarket.getValue(1));
        assertNumEquals(10, markToMarket.getValue(2));

        var realized = new CumulativePnL(series, record, EquityCurveMode.REALIZED);
        assertNumEquals(0, realized.getValue(0));
        assertNumEquals(0, realized.getValue(1));
        assertNumEquals(0, realized.getValue(2));
    }

    @Test
    public void positionConstructorUsesMarkToMarket() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));

        var pnl = new CumulativePnL(series, position);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(10, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
    }

    @Test
    public void positionConstructorUsesRealizedMode() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));

        var pnl = new CumulativePnL(series, position, EquityCurveMode.REALIZED);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
    }

    @Test
    public void cumulativePnL_markToMarket_doesNotUseFutureExitPriceWhenExitAfterFinalIndex() {
        var series = new MockBarSeriesBuilder().withData(10d, 11d, 12d, 13d, 100d).build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(4, series.getBar(4).getClosePrice(), series.numFactory().one());

        var cumulativePnL = new CumulativePnL(series, tradingRecord, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        var expected = series.getBar(2).getClosePrice().minus(series.getBar(0).getClosePrice());
        assertNumEquals(cumulativePnL.getValue(2), expected);
    }

    @Test
    public void cumulativePnL_ignore_skipsPositionsThatAreOpenAtFinalIndex() {
        var series = new MockBarSeriesBuilder().withData(10d, 11d, 12d, 13d, 100d).build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(4, series.getBar(4).getClosePrice(), series.numFactory().one());

        var cumulativePnL = new CumulativePnL(series, tradingRecord, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);

        assertNumEquals(cumulativePnL.getValue(2), series.numFactory().zero());
    }

}
