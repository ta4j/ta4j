/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.function.IntPredicate;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.TriggeredRule.ResetPolicy;
import org.ta4j.core.rules.TriggeredRule.Stage;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.RuleSerialization;

public class TriggeredRuleTest {

    @Test
    public void alwaysActiveRuleIsEvaluatedOnEveryBar() {
        TriggeredRule rule = new TriggeredRule(BooleanRule.TRUE);

        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(10, new BaseTradingRecord()));
    }

    @Test
    public void stageArmsAndDelegatesWithinActivationWindow() {
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE,
                new Stage(new IndexRule(2), new IndexRule(4), 2, false, ResetPolicy.ON_SATISFACTION));

        assertFalse(rule.isSatisfied(1, null));
        assertFalse(rule.isSatisfied(2, null));
        assertFalse(rule.isSatisfied(3, null));
        assertTrue(rule.isSatisfied(4, null));
        assertFalse(rule.isSatisfied(4, null));
    }

    @Test
    public void zeroActivationWindowAllowsOnlySameBarDelegate() {
        TriggeredRule sameBar = new TriggeredRule(BooleanRule.FALSE,
                new Stage(new IndexRule(2), new IndexRule(2), 0, false, ResetPolicy.ON_SATISFACTION));
        TriggeredRule nextBar = new TriggeredRule(BooleanRule.FALSE,
                new Stage(new IndexRule(2), new IndexRule(3), 0, false, ResetPolicy.ON_SATISFACTION));

        assertTrue(sameBar.isSatisfied(2, null));
        assertFalse(nextBar.isSatisfied(2, null));
        assertFalse(nextBar.isSatisfied(3, null));
    }

    @Test
    public void unboundedActivationWindowStaysArmedUntilDelegateSatisfies() {
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE, new Stage(new IndexRule(1), new IndexRule(100),
                TriggeredRule.UNBOUNDED_WINDOW, false, ResetPolicy.ON_SATISFACTION));

        assertFalse(rule.isSatisfied(1, null));
        assertFalse(rule.isSatisfied(50, null));
        assertTrue(rule.isSatisfied(100, null));
    }

    @Test
    public void canPrimeStatefulDelegateWhileInactive() {
        CountingRule delegate = new CountingRule(index -> index >= 3);
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE, new Stage(new IndexRule(3), delegate,
                TriggeredRule.UNBOUNDED_WINDOW, true, ResetPolicy.ON_SATISFACTION));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, null));
        assertFalse(rule.isSatisfied(2, null));
        assertEquals(3, delegate.evaluations);
        assertTrue(rule.isSatisfied(3, null));
        assertEquals(4, delegate.evaluations);
    }

    @Test
    public void explicitResetRuleClearsMatchingStagesBeforeDelegatesRun() {
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE, new IndexRule(2), new Stage(new IndexRule(1),
                new IndexRule(2), TriggeredRule.UNBOUNDED_WINDOW, false, ResetPolicy.ON_EXPLICIT_RESET_RULE));

        assertFalse(rule.isSatisfied(1, null));
        assertFalse(rule.isSatisfied(2, null));
    }

    @Test
    public void positionChangeResetClearsMatchingStages() {
        TradingRecord tradingRecord = new BaseTradingRecord();
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE, new Stage(new IndexRule(0), new IndexRule(2),
                TriggeredRule.UNBOUNDED_WINDOW, false, ResetPolicy.ON_POSITION_CHANGE));

        assertFalse(rule.isSatisfied(0, tradingRecord));
        tradingRecord.enter(1);
        assertFalse(rule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void resetAllStagesOnSatisfactionClearsEveryArmedStage() {
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE, null, true,
                new Stage(new IndexRule(1), new IndexRule(2), TriggeredRule.UNBOUNDED_WINDOW, false,
                        ResetPolicy.ON_POSITION_CHANGE),
                new Stage(new IndexRule(1), new IndexRule(3), TriggeredRule.UNBOUNDED_WINDOW, false,
                        ResetPolicy.ON_POSITION_CHANGE));

        assertFalse(rule.isSatisfied(1, null));
        assertTrue(rule.isSatisfied(2, null));
        assertFalse(rule.isSatisfied(3, null));
    }

    @Test
    public void evaluationRestartAtEarlierIndexClearsArmedStages() {
        TradingRecord tradingRecord = new BaseTradingRecord();
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE, new Stage(new IndexRule(1), new IndexRule(2),
                TriggeredRule.UNBOUNDED_WINDOW, false, ResetPolicy.ON_EXPLICIT_RESET_RULE));

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void changedTradingRecordClearsArmedStages() {
        TradingRecord firstRecord = new BaseTradingRecord();
        TradingRecord secondRecord = new BaseTradingRecord();
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE, new Stage(new IndexRule(1), new IndexRule(2),
                TriggeredRule.UNBOUNDED_WINDOW, false, ResetPolicy.ON_EXPLICIT_RESET_RULE));

        assertFalse(rule.isSatisfied(1, firstRecord));
        assertFalse(rule.isSatisfied(2, secondRecord));
    }

    @Test
    public void omniStyleExitTriggersInvalidationOnStopLossTouch() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 96, 90).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        TriggeredRule exitRule = new TriggeredRule(new StopLossRule(closePrice, 10));
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        Num amount = series.numFactory().one();

        tradingRecord.enter(0, series.numFactory().hundred(), amount);

        assertFalse(exitRule.isSatisfied(1, tradingRecord));
        assertTrue(exitRule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void omniStyleExitTriggersTimeoutWhenTargetAndInvalidationDoNotFire() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 101, 102, 103, 104, 105).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Rule invalidationOrTimeout = new StopLossRule(closePrice, 10).or(new OpenedPositionMinimumBarCountRule(5));
        TriggeredRule exitRule = new TriggeredRule(invalidationOrTimeout,
                new Stage(new StopGainRule(closePrice, 10),
                        new TrailingStopLossRule(closePrice, series.numFactory().numOf(5)),
                        TriggeredRule.UNBOUNDED_WINDOW, false, ResetPolicy.ON_POSITION_CHANGE));
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        assertFalse(exitRule.isSatisfied(4, tradingRecord));
        assertTrue(exitRule.isSatisfied(5, tradingRecord));
    }

    @Test
    public void omniStyleTargetCanArmTrailingExitInsteadOfExitingImmediately() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 105, 110, 112, 100).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Rule invalidationOrTimeout = new StopLossRule(closePrice, 20).or(new OpenedPositionMinimumBarCountRule(10));
        TriggeredRule exitRule = new TriggeredRule(invalidationOrTimeout,
                new Stage(new StopGainRule(closePrice, 10),
                        new TrailingStopLossRule(closePrice, series.numFactory().numOf(5)),
                        TriggeredRule.UNBOUNDED_WINDOW, false, ResetPolicy.ON_POSITION_CHANGE));
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        tradingRecord.enter(0, series.numFactory().hundred(), series.numFactory().one());

        assertFalse(exitRule.isSatisfied(1, tradingRecord));
        assertFalse(exitRule.isSatisfied(2, tradingRecord));
        assertFalse(exitRule.isSatisfied(3, tradingRecord));
        assertTrue(exitRule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void constructorValidationRejectsInvalidStages() {
        Rule trigger = new IndexRule(1);
        Rule delegate = new IndexRule(2);

        assertThrows(NullPointerException.class,
                () -> new Stage(null, delegate, 1, false, ResetPolicy.ON_SATISFACTION));
        assertThrows(NullPointerException.class, () -> new Stage(trigger, null, 1, false, ResetPolicy.ON_SATISFACTION));
        assertThrows(NullPointerException.class, () -> new Stage(trigger, delegate, 1, false, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Stage(trigger, delegate, -1, false, ResetPolicy.ON_SATISFACTION));
        new Stage(trigger, delegate);
        new Stage(trigger, delegate, 1);
        assertThrows(NullPointerException.class, () -> new TriggeredRule(BooleanRule.FALSE, (Stage[]) null));
        assertThrows(NullPointerException.class, () -> new TriggeredRule(BooleanRule.FALSE, new Stage[] { null }));
    }

    @Test
    public void arrayConstructorDefensivelyValidatesConfiguration() {
        Rule[] triggers = { new BooleanRule(true) };
        Rule[] delegates = { new BooleanRule(false) };
        int[] windows = { TriggeredRule.UNBOUNDED_WINDOW };
        boolean[] priming = { true };
        ResetPolicy[] policies = { ResetPolicy.ON_SATISFACTION };

        new TriggeredRule(BooleanRule.FALSE, true, triggers, delegates, windows, priming, policies);

        assertThrows(IllegalArgumentException.class,
                () -> new TriggeredRule(BooleanRule.FALSE, true, triggers, new Rule[0], windows, priming, policies));
        assertThrows(IllegalArgumentException.class, () -> new TriggeredRule(BooleanRule.FALSE, true, triggers,
                delegates, new int[] { -1 }, priming, policies));
        assertThrows(NullPointerException.class, () -> new TriggeredRule(BooleanRule.FALSE, true, new Rule[] { null },
                delegates, windows, priming, policies));
    }

    @Test
    public void serializeAndDeserializeWithoutExplicitResetRule() {
        TriggeredRule rule = new TriggeredRule(new BooleanRule(false), true, new Rule[] { new BooleanRule(true) },
                new Rule[] { new BooleanRule(false) }, new int[] { TriggeredRule.UNBOUNDED_WINDOW },
                new boolean[] { true }, new ResetPolicy[] { ResetPolicy.ON_SATISFACTION });

        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void serializeAndDeserializeWithExplicitResetRule() {
        TriggeredRule rule = new TriggeredRule(new BooleanRule(false), new BooleanRule(true), false,
                new Rule[] { new BooleanRule(true) }, new Rule[] { new BooleanRule(false) },
                new int[] { TriggeredRule.UNBOUNDED_WINDOW }, new boolean[] { false },
                new ResetPolicy[] { ResetPolicy.ON_EXPLICIT_RESET_RULE });

        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void serializationExcludesRuntimeStageStateAfterEvaluation() {
        TriggeredRule rule = new TriggeredRule(BooleanRule.FALSE, new Stage(new IndexRule(1), new IndexRule(3),
                TriggeredRule.UNBOUNDED_WINDOW, false, ResetPolicy.ON_POSITION_CHANGE));

        assertFalse(rule.isSatisfied(1, new BaseTradingRecord()));

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);
        String json = ComponentSerialization.toJson(descriptor);

        assertFalse(json.contains("stageStates"));
        assertFalse(json.contains("lastPositionSnapshot"));
        assertFalse(json.contains("lastTradingRecord"));
        assertFalse(json.contains("lastEvaluatedIndex"));
        assertFalse(json.contains("evaluationStarted"));
        assertFalse(json.contains("hasPositionResetStages"));
        assertFalse(json.contains("hasExplicitResetStages"));
    }

    private static final class IndexRule extends AbstractRule {

        private final int satisfiedIndex;

        private IndexRule(int satisfiedIndex) {
            this.satisfiedIndex = satisfiedIndex;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            boolean satisfied = index == satisfiedIndex;
            traceIsSatisfied(index, satisfied);
            return satisfied;
        }
    }

    private static final class CountingRule extends AbstractRule {

        private final IntPredicate satisfiedIndex;
        private int evaluations;

        private CountingRule(IntPredicate satisfiedIndex) {
            this.satisfiedIndex = satisfiedIndex;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            evaluations++;
            boolean satisfied = satisfiedIndex.test(index);
            traceIsSatisfied(index, satisfied);
            return satisfied;
        }
    }
}
