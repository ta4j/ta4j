/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Position;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.CumulativePnL;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.PerformanceIndicator;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DrawdownTest extends AbstractIndicatorTest<org.ta4j.core.Indicator<Num>, Num> {

    public DrawdownTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void noDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        var close = new ClosePriceIndicator(series);

        var amount = Drawdown.amount(series, null, close, true);
        var length = Drawdown.length(series, null, close, true);
        assertNumEquals(0, amount);
        assertNumEquals(0, length);
    }

    @Test
    public void defaultPerformanceBoundsOnAnEmptySeriesAreNeverRead() {
        BarSeries empty = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();
        AtomicInteger reads = new AtomicInteger();
        // A third-party curve relying on the interface's default bounds reports
        // [-1, -1] for an empty series; index -1 must never be read.
        PerformanceIndicator curve = defaultBoundsCurve(empty, index -> {
            reads.incrementAndGet();
            if (index < 0) {
                throw new IndexOutOfBoundsException("index " + index);
            }
            return numFactory.one();
        });

        assertNumEquals(0, Drawdown.amount(empty, null, curve, true));
        assertNumEquals(0, Drawdown.length(empty, null, curve, false));
        assertEquals(0, reads.get());
    }

    @Test
    public void defaultPerformanceBoundsStopAtAnExplicitRecordEnd() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 8, 9, 1).build();
        var close = new ClosePriceIndicator(series);
        // A third-party curve reports the full series; the record ends at 2, so the
        // fall to 1 at index 3 lies after the analysed window.
        PerformanceIndicator curve = defaultBoundsCurve(series, close::getValue);
        var record = new BaseTradingRecord(TradeType.BUY, 0, 2, new ZeroCostModel(), new ZeroCostModel());

        assertNumEquals(0.2, Drawdown.amount(series, record, curve, true));
        assertNumEquals(2, Drawdown.amount(series, record, curve, false));
        assertNumEquals(1, Drawdown.length(series, record, curve, true));
    }

    private static PerformanceIndicator defaultBoundsCurve(BarSeries series, IntFunction<Num> values) {
        return new PerformanceIndicator() {
            @Override
            public Num getValue(int index) {
                return values.apply(index);
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
            }
        };
    }

    @Test
    public void relativeDrawdownAndLength() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 7, 9, 5, 11).build();
        var close = new ClosePriceIndicator(series);

        var amount = Drawdown.amount(series, null, close, true);
        var length = Drawdown.length(series, null, close, true);
        assertNumEquals(0.5, amount);
        assertNumEquals(3, length);
    }

    @Test
    public void absoluteDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 80, 120, 90).build();
        var close = new ClosePriceIndicator(series);

        var amount = Drawdown.amount(series, null, close, false);
        var length = Drawdown.length(series, null, close, false);
        assertNumEquals(30, amount);
        assertNumEquals(1, length);
    }

    @Test
    public void limitsToTradingRecordRange() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 2, 9, 3, 8).build();
        var close = new ClosePriceIndicator(series);
        var record = new BaseTradingRecord(TradeType.BUY, 2, 4, new ZeroCostModel(), new ZeroCostModel());

        var amount = Drawdown.amount(series, record, close);
        var length = Drawdown.length(series, record, close);
        assertNumEquals(0.6667, amount);
        assertNumEquals(1, length);
    }

    @Test
    public void scanStopsAtTerminalEndIndexWithoutWraparound() {
        BarSeries series = ConstrainedSeriesSupport.terminalOneBarSeries("terminal", numFactory, 100d);
        var record = new BaseTradingRecord(Trade.buyAt(Integer.MAX_VALUE, series),
                Trade.sellAt(Integer.MAX_VALUE, series));
        CashFlow curve = new CashFlow(series, record);
        int begin = series.getBeginIndex();
        int end = record.getEndIndex(series);
        Indicator<Num> guardedCurve = new Indicator<>() {
            @Override
            public Num getValue(int index) {
                if (index < begin || index > end) {
                    throw new AssertionError("scan queried out-of-range index " + index);
                }
                return curve.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return curve.getCountOfUnstableBars();
            }

            @Override
            public BarSeries getBarSeries() {
                return curve.getBarSeries();
            }
        };

        Num amount = Drawdown.amount(series, record, guardedCurve, true);

        assertNumEquals(0, amount);
    }

    @Test
    public void ignoresAnExitAfterTheWindow() {
        // Entry 100, window close 110, exit after the window at 55: the window
        // never saw the fall, so there is no drawdown.
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("drawdown-trailing-exit", numFactory, 1,
                100d, 110d, 55d);
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        assertNumEquals(0, Drawdown.amount(series, record, new CashFlow(series, record)));
        assertNumEquals(0, Drawdown.amount(series, record, new CumulativePnL(series, record), false));
    }

    @Test
    public void cashFlowHonorsExplicitTradingRecordStart() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 100d, 50d)
                .build();
        var record = new BaseTradingRecord(TradeType.BUY, 2, 3, new ZeroCostModel(), new ZeroCostModel());
        record.operate(Trade.buyAt(2, series));
        record.operate(Trade.sellAt(3, series));
        CashFlow cashFlow = new CashFlow(series, record);

        assertNumEquals(1, Drawdown.length(series, record, cashFlow));
    }

    @Test
    public void absoluteLengthWithoutNewPeakIsMeasuredFromTheScannedWindowStart() {
        // The P&L curve starts at zero at the record start and only falls, so
        // the initial peak is never replaced: the length must count from the
        // record start, not from the series begin two bars earlier.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 100d, 80d, 50d)
                .build();
        var record = new BaseTradingRecord(TradeType.BUY, 2, 4, new ZeroCostModel(), new ZeroCostModel());
        record.operate(Trade.buyAt(2, series));
        record.operate(Trade.sellAt(4, series));
        CumulativePnL pnl = new CumulativePnL(series, record);

        assertNumEquals(50, Drawdown.amount(series, record, pnl, false));
        assertNumEquals(2, Drawdown.length(series, record, pnl, false));
    }

    @Test
    public void capturedCashFlowBoundsSurviveSeriesPruning() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 55d).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        CashFlow cashFlow = new CashFlow(series, record);
        series.setMaximumBarCount(2);

        assertNumEquals(0.5, Drawdown.amount(series, record, cashFlow));
    }

    @Test
    public void drawdownScansMatchFreshSeriesAcrossWindowShapesAndOpenHandling() {
        for (ConstrainedSeriesSupport.CriterionWindowFixture fixture : ConstrainedSeriesSupport
                .criterionWindowFixtures(numFactory)) {
            for (EquityCurveMode mode : EquityCurveMode.values()) {
                for (OpenPositionHandling handling : OpenPositionHandling.values()) {
                    PerformanceIndicator actualCashFlow = new CashFlow(fixture.series(), fixture.tradingRecord(), mode,
                            handling);
                    PerformanceIndicator expectedCashFlow = new CashFlow(fixture.equivalentSeries(),
                            fixture.equivalentRecord(mode), mode, handling);
                    PerformanceIndicator actualPnl = new CumulativePnL(fixture.series(), fixture.tradingRecord(), mode,
                            handling);
                    PerformanceIndicator expectedPnl = new CumulativePnL(fixture.equivalentSeries(),
                            fixture.equivalentRecord(mode), mode, handling);
                    PerformanceIndicator[] actualCurves = { actualCashFlow, actualPnl };
                    PerformanceIndicator[] expectedCurves = { expectedCashFlow, expectedPnl };
                    for (int curveIndex = 0; curveIndex < actualCurves.length; curveIndex++) {
                        for (boolean relative : new boolean[] { true, false }) {
                            String scenario = fixture.name() + ": " + mode + "/" + handling + "/curve=" + curveIndex
                                    + "/relative=" + relative;
                            Num actualAmount = Drawdown.amount(fixture.series(), fixture.tradingRecord(),
                                    actualCurves[curveIndex], relative);
                            Num expectedAmount = Drawdown.amount(fixture.equivalentSeries(),
                                    fixture.equivalentRecord(mode), expectedCurves[curveIndex], relative);
                            assertEquals(scenario + "/amount", expectedAmount.doubleValue(), actualAmount.doubleValue(),
                                    1e-10);
                            Num actualLength = Drawdown.length(fixture.series(), fixture.tradingRecord(),
                                    actualCurves[curveIndex], relative);
                            Num expectedLength = Drawdown.length(fixture.equivalentSeries(),
                                    fixture.equivalentRecord(mode), expectedCurves[curveIndex], relative);
                            assertEquals(scenario + "/length", expectedLength.doubleValue(), actualLength.doubleValue(),
                                    1e-10);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void negativeGenericCurveRetainsZeroPeakAtWindowStart() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        Indicator<Num> curve = new Indicator<Num>() {
            @Override
            public Num getValue(int index) {
                return index == 1 ? numFactory.numOf(-10) : numFactory.numOf(-20);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }
        };
        var record = new BaseTradingRecord(TradeType.BUY, 1, 2, new ZeroCostModel(), new ZeroCostModel());

        assertNumEquals(20, Drawdown.amount(series, record, curve, false));
        assertNumEquals(1, Drawdown.length(series, record, curve, false));
    }

    @Test
    public void initialCashFlowLossSpansFromInitialCapital() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        CashFlow cashFlow = new CashFlow(series, new BaseTradingRecord()) {
            @Override
            public Num getValue(int index) {
                return numFactory.numOf(0.9);
            }

            @Override
            public boolean hasInitialReturn() {
                return true;
            }
        };
        var record = new BaseTradingRecord(TradeType.BUY, 1, 2, new ZeroCostModel(), new ZeroCostModel());

        assertNumEquals(1, Drawdown.length(series, null, cashFlow, true));
        assertNumEquals(0, Drawdown.length(series, record, cashFlow, true));
    }
}