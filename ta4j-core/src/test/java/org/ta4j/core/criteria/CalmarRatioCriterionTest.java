/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.alwaysInvested;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.TimeConstants;

public class CalmarRatioCriterionTest extends AbstractCriterionTest {

    public CalmarRatioCriterionTest(NumFactory numFactory) {
        super(params -> new CalmarRatioCriterion(), numFactory);
    }

    @Test
    public void calculatesExpectedValueForMixedReturnsTradingRecord() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_mixed", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void annualizesWithTheTimesOfTheBarsTheCashFlowWasBuiltFrom() {
        AtomicBoolean armed = new AtomicBoolean();
        AtomicInteger outermostLeases = new AtomicInteger();
        AtomicReference<Runnable> writer = new AtomicReference<>();
        // Lets a feed writer replace the last bar before the fifth outermost read
        // lease of the armed calculation: after the cash flow was built from the
        // original bar, before the bar times used for annualizing are read.
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock() {
            private final ReadLock replacingReadLock = new ReadLock(this) {
                @Override
                public void lock() {
                    if (armed.get() && getReadHoldCount() == 0 && outermostLeases.incrementAndGet() == 5) {
                        armed.set(false);
                        writer.get().run();
                    }
                    super.lock();
                }
            };

            @Override
            public ReadLock readLock() {
                return replacingReadLock;
            }
        };
        BarSeries yearly = buildYearlySeries("calmar-replaced-last-bar", new double[] { 100d, 80d, 120d });
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(yearly, lock);
        // The replacement moves both the close and the end time, so the return and
        // the elapsed years disagree unless both come from the same bar.
        Bar replacement = series.barBuilder()
                .timePeriod(Duration.ofDays(365))
                .endTime(yearly.getLastBar().getEndTime().plus(Duration.ofDays(3 * 365)))
                .openPrice(150d)
                .highPrice(150d)
                .lowPrice(150d)
                .closePrice(150d)
                .volume(1)
                .build();
        writer.set(() -> series.addBar(replacement, true));
        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        CalmarRatioCriterion criterion = new CalmarRatioCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        armed.set(true);
        Num raced = criterion.calculate(series, tradingRecord);
        armed.set(false);
        Num settled = criterion.calculate(series, tradingRecord);

        assertNumEquals(150d, series.getBar(2).getClosePrice());
        assertEquals(settled, raced);
    }

    @Test
    public void scoresAnExitAfterTheWindowAsOpenAtTheWindowEnd() {
        double[] closes = new double[] { 100d, 110d, 55d };
        BarSeries constrained = ConstrainedSeriesSupport.trailingConstrainedSeries("calmar-trailing-exit", numFactory,
                1, closes);
        BaseTradingRecord constrainedRecord = new BaseTradingRecord(Trade.buyAt(0, constrained),
                Trade.sellAt(2, constrained));
        BarSeries truncated = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d).build();
        BaseTradingRecord openAtWindowEnd = new BaseTradingRecord(Trade.buyAt(0, truncated));
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        Num constrainedValue = criterion.calculate(constrained, constrainedRecord);

        assertNumEquals(criterion.calculate(truncated, openAtWindowEnd), constrainedValue, 1e-12);
        assertTrue("the later exit at 55 must not create a drawdown", constrainedValue.isPositive());
    }

    @Test
    public void explicitRecordStartExcludesNeutralCashFlowPrefixFromAnnualization() {
        double[] closes = new double[] { 100d, 100d, 100d, 50d };
        BarSeries series = buildYearlySeries("calmar-explicit-start", closes);
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, 2, 3, new ZeroCostModel(), new ZeroCostModel());
        record.operate(Trade.buyAt(2, series));
        record.operate(Trade.sellAt(3, series));

        double years = Duration.between(series.getBar(2).getEndTime(), series.getBar(3).getEndTime()).getSeconds()
                / TimeConstants.SECONDS_PER_YEAR;
        double expected = (Math.pow(0.5d, 1d / years) - 1d) / 0.5d;

        assertNumEquals(numFactory.numOf(expected), getCriterion().calculate(series, record), 1e-12);
    }

    @Test
    public void exitRealizedOnTheWindowsFirstBarCountsTowardsTheAnnualizedReturn() {
        BarSeries series = ConstrainedSeriesSupport.offsetSeries("calmar-first-bar-exit",
                buildYearlySeries("calmar-first-bar-exit-source", new double[] { 100d, 100d, 100d, 100d }), 1, 3);
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());
        record.enter(0, numFactory.hundred(), numFactory.one());
        record.exit(1, numFactory.numOf(105), numFactory.one());

        double years = Duration.between(series.getBar(1).getEndTime(), series.getBar(3).getEndTime()).getSeconds()
                / TimeConstants.SECONDS_PER_YEAR;
        double expected = Math.pow(1.05d, 1d / years) - 1d;

        assertNumEquals(numFactory.numOf(expected), getCriterion().calculate(series, record), 1e-9);
    }

    @Test
    public void explicitRecordEndExcludesFlatCashFlowSuffixFromAnnualization() {
        double[] closes = new double[] { 100d, 80d, 120d, 120d, 120d, 120d };
        double[] recordedCloses = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar-explicit-end", closes);
        BarSeries recorded = buildYearlySeries("calmar-explicit-end-recorded", recordedCloses);
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, 0, 2, new ZeroCostModel(), new ZeroCostModel());
        record.operate(Trade.buyAt(0, series));
        record.operate(Trade.sellAt(2, series));

        Num actual = getCriterion().calculate(series, record);

        assertNumEquals(numFactory.numOf(referenceCalmar(recorded, recordedCloses)), actual, 1e-12);
    }

    @Test
    public void ignoresTradesOutsideAnEmptyLogicalWindow() {
        BarSeries rawOnly = ConstrainedSeriesSupport.emptyLogicalSeries("calmar-raw-only", numFactory, 100d, 50d);
        BaseTradingRecord rawOnlyRecord = new BaseTradingRecord(Trade.buyAt(0, rawOnly), Trade.sellAt(1, rawOnly));

        assertNumEquals(0, getCriterion().calculate(rawOnly, rawOnlyRecord));
    }

    @Test
    public void returnsPercentageRepresentation() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_percentage", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = new CalmarRatioCriterion(ReturnRepresentation.PERCENTAGE);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected * 100d), actual, 1e-12);
    }

    @Test
    public void returnsMultiplicativeRepresentation() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_multiplicative", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = new CalmarRatioCriterion(ReturnRepresentation.MULTIPLICATIVE);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(1d + expected), actual, 1e-12);
    }

    @Test
    public void calculatesExpectedValueForPositiveReturnsTradingRecordWithNoDrawdown() {
        double[] closes = new double[] { 100d, 110d, 121d };
        BarSeries series = buildYearlySeries("calmar_positive", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void calculatesExpectedValueForNegativeReturnsTradingRecord() {
        double[] closes = new double[] { 100d, 70d, 80d };
        BarSeries series = buildYearlySeries("calmar_negative", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void returnsZeroWhenThereAreNoReturnObservations() {
        BarSeries series = buildYearlySeries("calmar_one_bar", new double[] { 100d });
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        Num actual = criterion.calculate(series, new BaseTradingRecord());

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenTradingRecordIsNull() {
        BarSeries series = buildYearlySeries("calmar_null_record", new double[] { 100d, 120d });
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        Num actual = criterion.calculate(series, (TradingRecord) null);

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void calculatesExpectedValueForClosedPosition() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_closed_position", closes);

        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(),
                numFactory.one());
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), numFactory.one());
        Position position = tradingRecord.getPositions().getFirst();

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, position);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void openPositionHandlingIgnoreReturnsZeroForOpenPosition() {
        BarSeries series = buildYearlySeries("calmar_open_position", new double[] { 100d, 120d, 80d });
        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(),
                numFactory.one());

        CalmarRatioCriterion markToMarket = new CalmarRatioCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CalmarRatioCriterion ignoreOpen = new CalmarRatioCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);
        CalmarRatioCriterion realized = new CalmarRatioCriterion(EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);

        Num markToMarketValue = markToMarket.calculate(series, tradingRecord);
        Num ignoreOpenValue = ignoreOpen.calculate(series, tradingRecord);
        Num realizedValue = realized.calculate(series, tradingRecord);

        assertTrue(markToMarketValue.isNegative());
        assertNumEquals(numFactory.zero(), ignoreOpenValue, 0d);
        assertNumEquals(numFactory.zero(), realizedValue, 0d);
    }

    @Test
    public void returnsNaNWhenBeginValueCannotBeUsed() {
        BarSeries series = buildYearlySeries("calmar_zero_begin", new double[] { 0d, 120d, 80d });
        CalmarRatioCriterion criterion = new CalmarRatioCriterion(ReturnRepresentation.DECIMAL);

        Num actual = criterion.calculate(series, alwaysInvested(series));

        assertTrue(actual.isNaN());
    }

    @Test
    public void exposesReturnRepresentation() {
        CalmarRatioCriterion criterion = new CalmarRatioCriterion(ReturnRepresentation.PERCENTAGE);

        assertEquals(Optional.of(ReturnRepresentation.PERCENTAGE), criterion.getReturnRepresentation());
    }

    @Test
    public void betterThanUsesHigherValuesAsBetter() {
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.zero()));
        assertFalse(criterion.betterThan(numFactory.zero(), numFactory.one()));
    }

    private BarSeries buildYearlySeries(String name, double[] closes) {
        BarSeries series = getBarSeries(name);
        Instant start = Instant.parse("2020-01-01T00:00:00Z");

        for (int i = 0; i < closes.length; i++) {
            Instant endTime = start.plus(Duration.ofDays(365L * i));
            double close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(365))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        }
        return series;
    }

    private double referenceCalmar(BarSeries series, double[] closes) {
        double annualizedReturn = referenceAnnualizedReturn(series, closes);
        double maximumDrawdown = referenceMaximumDrawdown(closes);
        if (maximumDrawdown == 0d) {
            return annualizedReturn;
        }
        return annualizedReturn / maximumDrawdown;
    }

    private double referenceAnnualizedReturn(BarSeries series, double[] closes) {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        if (endIndex <= beginIndex) {
            return 0d;
        }

        double elapsedSeconds = Duration
                .between(series.getBar(beginIndex).getEndTime(), series.getBar(endIndex).getEndTime())
                .getSeconds();
        if (elapsedSeconds <= 0d) {
            return 0d;
        }

        double years = elapsedSeconds / TimeConstants.SECONDS_PER_YEAR;
        double totalReturn = closes[closes.length - 1] / closes[0];
        return Math.pow(totalReturn, 1d / years) - 1d;
    }

    private double referenceMaximumDrawdown(double[] closes) {
        double peak = closes[0];
        double maximumDrawdown = 0d;
        for (double close : closes) {
            if (close > peak) {
                peak = close;
            }
            double drawdown = (peak - close) / peak;
            maximumDrawdown = Math.max(maximumDrawdown, drawdown);
        }
        return maximumDrawdown;
    }

    @Test
    public void matchesFreshSeriesAcrossWindowShapesAndPositionBoundaries() {
        for (ConstrainedSeriesSupport.CriterionWindowFixture fixture : ConstrainedSeriesSupport
                .criterionWindowFixtures(numFactory)) {
            for (EquityCurveMode mode : EquityCurveMode.values()) {
                for (OpenPositionHandling handling : OpenPositionHandling.values()) {
                    for (ReturnRepresentation representation : ReturnRepresentation.values()) {
                        CalmarRatioCriterion criterion = new CalmarRatioCriterion(representation, mode, handling);
                        String scenario = fixture.name() + "/" + mode + "/" + handling + "/" + representation;
                        assertEquals(scenario + " record",
                                criterion.calculate(fixture.series(), fixture.tradingRecord()),
                                criterion.calculate(fixture.equivalentSeries(), fixture.equivalentRecord(mode)));
                        if (fixture.equivalentPosition(mode) != null) {
                            assertEquals(scenario + " position",
                                    criterion.calculate(fixture.series(), fixture.position()),
                                    criterion.calculate(fixture.equivalentSeries(), fixture.equivalentPosition(mode)));
                        }
                    }
                }
            }
        }
    }
}
