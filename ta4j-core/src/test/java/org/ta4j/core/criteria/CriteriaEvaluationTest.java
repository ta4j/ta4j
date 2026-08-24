/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityBundle;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.criteria.drawdown.MaximumAbsoluteDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownBarLengthCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.MonteCarloMaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies that batch evaluation via {@link CriteriaEvaluation#evaluateAll}
 * returns exactly the same values as evaluating each criterion on its own, and
 * that the shared {@link EquityBundle} reuses one curve instance per
 * configuration key.
 */
public class CriteriaEvaluationTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public CriteriaEvaluationTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static List<AnalysisCriterion> criteria() {
        List<AnalysisCriterion> criteria = new ArrayList<>();
        criteria.add(new MaximumDrawdownCriterion());
        criteria.add(new MaximumDrawdownCriterion(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE));
        criteria.add(new MaximumDrawdownBarLengthCriterion());
        criteria.add(new MaximumAbsoluteDrawdownCriterion());
        criteria.add(new ReturnOverMaxDrawdownCriterion());
        criteria.add(new MonteCarloMaximumDrawdownCriterion());
        criteria.add(new CalmarRatioCriterion());
        criteria.add(new CalmarRatioCriterion(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE));
        criteria.add(new SharpeRatioCriterion(0.05));
        criteria.add(new SharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET));
        criteria.add(new SortinoRatioCriterion(0.02));
        // exercises the fallback path: builds no equity curves at all
        criteria.add(new NetProfitLossCriterion());
        return criteria;
    }

    private BarSeries series() {
        return new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 2d, 4d, 3d, 5d, 4d, 6d, 5d, 7d)
                .build();
    }

    private TradingRecord closedPositionsRecord(BarSeries series) {
        return new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(5, series), Trade.buyAt(6, series), Trade.sellAt(9, series));
    }

    private TradingRecord openPositionRecord(BarSeries series) {
        return new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(5, series), Trade.buyAt(9, series));
    }

    @Test
    public void batchEvaluationMatchesSequentialEvaluationWithClosedPositions() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        assertBatchMatchesSequential(series, tradingRecord);
    }

    @Test
    public void batchEvaluationMatchesSequentialEvaluationWithOpenPosition() {
        BarSeries series = series();
        TradingRecord tradingRecord = openPositionRecord(series);
        assertBatchMatchesSequential(series, tradingRecord);
    }

    @Test
    public void batchEvaluationMatchesSequentialEvaluationWithEmptyRecord() {
        BarSeries series = series();
        assertBatchMatchesSequential(series, new BaseTradingRecord());
    }

    private void assertBatchMatchesSequential(BarSeries series, TradingRecord tradingRecord) {
        List<AnalysisCriterion> criteria = criteria();
        Map<AnalysisCriterion, Num> batchResults = CriteriaEvaluation.evaluateAll(series, tradingRecord, criteria);
        assertEquals(criteria.size(), batchResults.size());
        for (AnalysisCriterion criterion : criteria) {
            Num sequential = criterion.calculate(series, tradingRecord);
            assertNumEquals(sequential, batchResults.get(criterion));
        }
    }

    @Test
    public void evaluateAllDispatchesToBundleAwareCriteria() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);

        var dispatched = new boolean[] { false };
        AnalysisCriterion bundleAware = new EquityBundleAwareCriterion(dispatched, numFactory);

        Map<AnalysisCriterion, Num> results = CriteriaEvaluation.evaluateAll(series, tradingRecord, bundleAware);

        assertSame(bundleAware, results.keySet().iterator().next());
        assertTrue("bundle-aware calculate must be dispatched", dispatched[0]);
        assertNumEquals(numFactory.numOf(1), results.get(bundleAware));
    }

    private static final class EquityBundleAwareCriterion implements AnalysisCriterion, EquityBundleAware {

        private final boolean[] dispatched;
        private final NumFactory numFactory;

        EquityBundleAwareCriterion(boolean[] dispatched, NumFactory numFactory) {
            this.dispatched = dispatched;
            this.numFactory = numFactory;
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord, EquityBundle equityBundle) {
            dispatched[0] = true;
            return numFactory.numOf(1);
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            return criterionValue1.isGreaterThan(criterionValue2);
        }
    }

    @Test
    public void evaluateAllRejectsNullTradingRecord() {
        BarSeries series = series();
        assertThrows(NullPointerException.class, () -> CriteriaEvaluation.evaluateAll(series, null, criteria()));
    }

    @Test
    public void evaluateAllSkipsDuplicateCriteriaBeforeEvaluating() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);

        CountingCriterion countingCriterion = new CountingCriterion(numFactory);

        Map<AnalysisCriterion, Num> results = CriteriaEvaluation.evaluateAll(series, tradingRecord,
                List.of(countingCriterion, countingCriterion));

        assertEquals(1, results.size());
        assertSame(countingCriterion, results.keySet().iterator().next());
        assertEquals(1, countingCriterion.calculations.get());
    }

    private static final class CountingCriterion implements AnalysisCriterion {

        private final NumFactory numFactory;
        private final AtomicInteger calculations;

        CountingCriterion(NumFactory numFactory) {
            this.numFactory = numFactory;
            this.calculations = new AtomicInteger();
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            calculations.incrementAndGet();
            return numFactory.one();
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            return criterionValue1.isGreaterThan(criterionValue2);
        }
    }
}
