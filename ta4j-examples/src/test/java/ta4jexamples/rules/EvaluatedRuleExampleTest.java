/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import ta4jexamples.rules.EvaluatedRuleExample.ConfidenceEvaluation;
import ta4jexamples.rules.EvaluatedRuleExample.ConfidenceGateRule;
import ta4jexamples.rules.EvaluatedRuleExample.ExposureEvaluation;
import ta4jexamples.rules.EvaluatedRuleExample.FlatWithTradeBudgetRule;
import ta4jexamples.rules.EvaluatedRuleExample.ScoreKind;

class EvaluatedRuleExampleTest {

    @Test
    void confidenceGateRejectsInvalidThresholds() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2).build();
        NumFactory numFactory = series.numFactory();
        Indicator<Num> score = new FixedIndicator<>(series, numFactory.zero(), numFactory.one());

        assertThrows(IllegalArgumentException.class,
                () -> new ConfidenceGateRule(score, NaN.NaN, ScoreKind.HEURISTIC_SCORE));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfidenceGateRule(score, numFactory.numOf(1.01), ScoreKind.CALIBRATED_PROBABILITY));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfidenceGateRule(score, numFactory.numOf(-0.01), ScoreKind.CALIBRATED_PROBABILITY));
        // Heuristic scores are not probabilities, so the [0, 1] bound does not apply.
        new ConfidenceGateRule(score, numFactory.numOf(55), ScoreKind.HEURISTIC_SCORE);
        new ConfidenceGateRule(score, numFactory.one(), ScoreKind.CALIBRATED_PROBABILITY);
    }

    @Test
    void zeroScoreIsAvailableAndInclusiveWhileNaNScoreIsUnavailable() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        NumFactory numFactory = series.numFactory();
        Indicator<Num> score = new FixedIndicator<>(series, numFactory.zero(), NaN.NaN, numFactory.numOf(0.49));
        ConfidenceGateRule rule = new ConfidenceGateRule(score, numFactory.zero(), ScoreKind.CALIBRATED_PROBABILITY);

        ConfidenceEvaluation zero = rule.evaluate(0);
        assertTrue(zero.isAvailable());
        assertTrue(rule.toBoolean(zero));

        ConfidenceEvaluation missing = rule.evaluate(1);
        assertFalse(missing.isAvailable());
        assertFalse(rule.toBoolean(missing));
        assertFalse(rule.isSatisfied(1));

        ConfidenceGateRule strict = new ConfidenceGateRule(score, numFactory.numOf(0.5),
                ScoreKind.CALIBRATED_PROBABILITY);
        assertFalse(strict.isSatisfied(2));
        assertEquals(numFactory.numOf(0.5), strict.evaluate(2).threshold());
    }

    @Test
    void warmUpBarsAreUnavailableFromTheSeriesBeginIndex() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 2, 3, 4, 5, 4, 5, 6).build();
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), 3);
        ConfidenceGateRule rule = new ConfidenceGateRule(rsi, series.numFactory().zero(), ScoreKind.HEURISTIC_SCORE);
        int firstStable = series.getBeginIndex() + rsi.getCountOfUnstableBars();

        for (int i = series.getBeginIndex(); i < firstStable; i++) {
            assertFalse(rule.evaluate(i).isAvailable(), "warm-up bar " + i);
            assertFalse(rule.isSatisfied(i), "warm-up bar " + i);
        }
        assertTrue(rule.evaluate(firstStable).isAvailable());
        assertTrue(rule.isSatisfied(firstStable));
    }

    @Test
    void exposureSnapshotKeepsObservationsAfterRecordMutation() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();
        BaseTradingRecord record = new BaseTradingRecord();
        FlatWithTradeBudgetRule rule = new FlatWithTradeBudgetRule(1);

        ExposureEvaluation flat = rule.evaluate(0, record);
        record.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());

        assertTrue(rule.toBoolean(flat));
        assertFalse(rule.isSatisfied(1, record));

        record.exit(1, series.getBar(1).getClosePrice(), series.numFactory().one());
        ExposureEvaluation exhausted = rule.evaluate(2, record);
        assertTrue(exhausted.flat());
        assertEquals(1, exhausted.closedPositions());
        assertFalse(rule.toBoolean(exhausted));
        assertTrue(rule.isSatisfied(2, null));
    }

    @Test
    void tradeBudgetMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new FlatWithTradeBudgetRule(0));
    }

    @Test
    void thresholdIsNormalizedToTheScoreNumFactory() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2)
                .build();
        NumFactory numFactory = series.numFactory();
        Indicator<Num> score = new FixedIndicator<>(series, numFactory.numOf(0.4), numFactory.numOf(0.6));
        ConfidenceGateRule rule = new ConfidenceGateRule(score, DoubleNumFactory.getInstance().numOf(0.5),
                ScoreKind.CALIBRATED_PROBABILITY);

        assertTrue(numFactory.produces(rule.evaluate(0).threshold()));
        assertEquals(numFactory.numOf(0.5), rule.evaluate(0).threshold());
        assertFalse(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
    }

    @Test
    void warmUpBoundaryDoesNotOverflowNearIntegerMaxValue() {
        int begin = Integer.MAX_VALUE - 10;
        BarSeries series = new BaseBarSeries("high-index",
                new MockBarSeriesBuilder().withData(new double[6]).build().getBarData()) {
            @Override
            public int getBeginIndex() {
                return begin;
            }

            @Override
            public int getEndIndex() {
                return Integer.MAX_VALUE - 1;
            }
        };
        Indicator<Num> score = new Indicator<>() {
            @Override
            public Num getValue(int index) {
                return series.numFactory().one();
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 20;
            }
        };
        ConfidenceGateRule rule = new ConfidenceGateRule(score, series.numFactory().zero(), ScoreKind.HEURISTIC_SCORE);

        for (int i = begin; i <= series.getEndIndex(); i++) {
            assertFalse(rule.evaluate(i).isAvailable(), "index " + i);
            assertFalse(rule.isSatisfied(i), "index " + i);
        }
    }
}
