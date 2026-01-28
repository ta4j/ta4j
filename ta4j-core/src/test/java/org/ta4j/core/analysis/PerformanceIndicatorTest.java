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

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PerformanceIndicatorTest extends AbstractIndicatorTest<PerformanceIndicator, Num> {

    public PerformanceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void recalculationWithEmptyRecordKeepsValuesStable() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        var tradingRecord = new BaseTradingRecord();

        var cashFlow = new CashFlow(series, tradingRecord);
        assertUnchanged(cashFlow, tradingRecord, series.getEndIndex(), numFactory.one());

        var cumulativePnL = new CumulativePnL(series, tradingRecord);
        assertUnchanged(cumulativePnL, tradingRecord, series.getEndIndex(), numFactory.zero());

        var returnsIndicator = new Returns(series, tradingRecord);
        assertUnchanged(returnsIndicator, tradingRecord, series.getEndIndex(), numFactory.zero());
    }

    @Test
    public void determineEndIndexUsesExitIndexWhenClosed() {
        var position = new Position();
        position.operate(0);
        position.operate(5);

        var result = testIndicator().determineEndIndex(position, 10, 20);
        assertEquals(5, result);
    }

    @Test
    public void determineEndIndexUsesFinalIndexBeforeExit() {
        var position = new Position();
        position.operate(0);
        position.operate(10);

        var result = testIndicator().determineEndIndex(position, 8, 20);
        assertEquals(8, result);
    }

    @Test
    public void determineEndIndexForOpenPositionClampsToMax() {
        var position = new Position();
        position.operate(0);

        var result = testIndicator().determineEndIndex(position, 15, 12);
        assertEquals(12, result);
    }

    @Test
    public void determineEndIndexForClosedPositionClampsToMax() {
        var position = new Position();
        position.operate(0);
        position.operate(15);

        var result = testIndicator().determineEndIndex(position, 20, 10);
        assertEquals(10, result);
    }

    @Test
    public void addCostSubtractsHoldingCostForLongTrades() {
        var raw = numFactory.hundred();
        var cost = numFactory.numOf(1.5);

        var result = testIndicator().addCost(raw, cost, true);
        assertNumEquals("98.5", result);
    }

    @Test
    public void addCostAddsHoldingCostForShortTrades() {
        var raw = numFactory.hundred();
        var cost = numFactory.numOf(1.5);

        var result = testIndicator().addCost(raw, cost, false);
        assertNumEquals("101.5", result);
    }

    @Test
    public void markToMarketSkipsFutureOpenPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d, 11d)
                .build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(10, numFactory.one(), numFactory.one());

        var calls = new AtomicInteger();
        var indicator = new PerformanceIndicator() {
            @Override
            public Num getValue(int index) {
                return numFactory.zero();
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }

            @Override
            public EquityCurveMode getEquityCurveMode() {
                return EquityCurveMode.MARK_TO_MARKET;
            }

            @Override
            public void calculatePosition(Position position, int finalIndex) {
                calls.incrementAndGet();
            }
        };

        indicator.calculate(tradingRecord, 5, OpenPositionHandling.MARK_TO_MARKET);

        assertEquals(0, calls.get());
    }

    private void assertUnchanged(PerformanceIndicator indicator, TradingRecord tradingRecord, int finalIndex,
            Num expectedValue) {
        indicator.calculate(tradingRecord, finalIndex, OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(expectedValue, indicator.getValue(finalIndex));
    }

    private PerformanceIndicator testIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d).build();
        return new PerformanceIndicator() {
            @Override
            public Num getValue(int index) {
                return numFactory.zero();
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }

            @Override
            public EquityCurveMode getEquityCurveMode() {
                return EquityCurveMode.MARK_TO_MARKET;
            }

            @Override
            public void calculatePosition(Position position, int finalIndex) {
                // no-op for testing default helpers
            }
        };
    }
}
