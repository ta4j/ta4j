/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;

/**
 * Keeps an always-active rule live while allowing one or more trigger/delegate
 * stages to arm and evaluate later bars.
 *
 * <p>
 * This rule is useful for staged exits and other lifecycle-based rule
 * compositions. A stage becomes active when its trigger rule is satisfied. Once
 * active, the stage delegates satisfaction checks to its delegate rule until
 * the stage expires or is reset by its configured {@link ResetPolicy}.
 * </p>
 *
 * <p>
 * Triggered state is tied to one sequential rule evaluation pass. Reusing the
 * same rule with a different {@link TradingRecord}, or evaluating an earlier
 * bar index after a later one, resets all armed stages to avoid leaking staged
 * state across backtest runs.
 * </p>
 *
 * @since 0.22.7
 */
public class TriggeredRule extends AbstractRule {

    /**
     * Sentinel activation window for stages that should stay active until they
     * explicitly satisfy or reset.
     *
     * @since 0.22.7
     */
    public static final int UNBOUNDED_WINDOW = Integer.MAX_VALUE;

    /**
     * Reset policies supported by {@link TriggeredRule} stages.
     *
     * @since 0.22.7
     */
    public enum ResetPolicy {

        /**
         * Reset the stage after its delegate rule is satisfied.
         */
        ON_SATISFACTION,

        /**
         * Reset the stage when the observed trading-record position changes.
         */
        ON_POSITION_CHANGE,

        /**
         * Reset the stage when the optional explicit reset rule is satisfied.
         */
        ON_EXPLICIT_RESET_RULE
    }

    /**
     * Immutable configuration for a single trigger/delegate stage.
     *
     * @param triggerRule                the rule that arms the stage
     * @param delegateRule               the rule evaluated once the stage is armed
     * @param activationWindowBars       the number of bars the stage may remain
     *                                   armed, or {@link #UNBOUNDED_WINDOW}
     * @param primeDelegateWhileInactive true to evaluate the delegate while the
     *                                   stage is inactive so stateful delegates can
     *                                   warm up
     * @param resetPolicy                the stage reset policy
     * @since 0.22.7
     */
    public record Stage(Rule triggerRule, Rule delegateRule, int activationWindowBars,
            boolean primeDelegateWhileInactive, ResetPolicy resetPolicy) {

        /**
         * Creates an unbounded stage that resets after delegate satisfaction.
         *
         * @param triggerRule  the rule that arms the stage
         * @param delegateRule the rule evaluated once the stage is armed
         * @since 0.22.7
         */
        public Stage(Rule triggerRule, Rule delegateRule) {
            this(triggerRule, delegateRule, UNBOUNDED_WINDOW);
        }

        /**
         * Creates a stage that resets after delegate satisfaction.
         *
         * @param triggerRule          the rule that arms the stage
         * @param delegateRule         the rule evaluated once the stage is armed
         * @param activationWindowBars the number of bars the stage may remain armed, or
         *                             {@link #UNBOUNDED_WINDOW}
         * @since 0.22.7
         */
        public Stage(Rule triggerRule, Rule delegateRule, int activationWindowBars) {
            this(triggerRule, delegateRule, activationWindowBars, false, ResetPolicy.ON_SATISFACTION);
        }

        /**
         * Creates a stage with the supplied lifecycle configuration.
         *
         * @since 0.22.7
         */
        public Stage {
            Objects.requireNonNull(triggerRule, "triggerRule");
            Objects.requireNonNull(delegateRule, "delegateRule");
            Objects.requireNonNull(resetPolicy, "resetPolicy");
            if (activationWindowBars < 0) {
                throw new IllegalArgumentException("activationWindowBars must be >= 0");
            }
        }
    }

    private final Rule alwaysActiveRule;
    private final Rule explicitResetRule;
    private final boolean resetAllStagesOnSatisfaction;
    private final Rule[] triggerRules;
    private final Rule[] delegateRules;
    private final int[] activationWindowBars;
    private final boolean[] primeDelegateWhileInactive;
    private final ResetPolicy[] resetPolicies;
    private final transient boolean hasPositionResetStages;
    private final transient boolean hasExplicitResetStages;
    private final transient StageState[] stageStates;

    private transient PositionSnapshot lastPositionSnapshot;
    private transient TradingRecord lastTradingRecord;
    private transient int lastEvaluatedIndex = Integer.MIN_VALUE;
    private transient boolean evaluationStarted;

    /**
     * Constructor.
     *
     * @param alwaysActiveRule the rule evaluated on every bar, or {@code null} to
     *                         use a permanently false base rule
     * @param stages           the triggered stages
     * @since 0.22.7
     */
    public TriggeredRule(Rule alwaysActiveRule, Stage... stages) {
        this(alwaysActiveRule, null, false, toStageArrays(stages));
    }

    /**
     * Constructor.
     *
     * @param alwaysActiveRule  the rule evaluated on every bar, or {@code null} to
     *                          use a permanently false base rule
     * @param explicitResetRule the rule that resets
     *                          {@link ResetPolicy#ON_EXPLICIT_RESET_RULE} stages
     *                          when satisfied
     * @param stages            the triggered stages
     * @since 0.22.7
     */
    public TriggeredRule(Rule alwaysActiveRule, Rule explicitResetRule, Stage... stages) {
        this(alwaysActiveRule, explicitResetRule, false, toStageArrays(stages));
    }

    /**
     * Constructor.
     *
     * @param alwaysActiveRule             the rule evaluated on every bar, or
     *                                     {@code null} to use a permanently false
     *                                     base rule
     * @param explicitResetRule            the rule that resets
     *                                     {@link ResetPolicy#ON_EXPLICIT_RESET_RULE}
     *                                     stages when satisfied
     * @param resetAllStagesOnSatisfaction true to reset every active stage whenever
     *                                     any delegate is satisfied
     * @param stages                       the triggered stages
     * @since 0.22.7
     */
    public TriggeredRule(Rule alwaysActiveRule, Rule explicitResetRule, boolean resetAllStagesOnSatisfaction,
            Collection<Stage> stages) {
        this(alwaysActiveRule, explicitResetRule, resetAllStagesOnSatisfaction, toStageArrays(stages));
    }

    /**
     * Constructor.
     *
     * @param alwaysActiveRule             the rule evaluated on every bar, or
     *                                     {@code null} to use a permanently false
     *                                     base rule
     * @param explicitResetRule            the rule that resets
     *                                     {@link ResetPolicy#ON_EXPLICIT_RESET_RULE}
     *                                     stages when satisfied
     * @param resetAllStagesOnSatisfaction true to reset every active stage whenever
     *                                     any delegate is satisfied
     * @param stages                       the triggered stages
     * @since 0.22.7
     */
    public TriggeredRule(Rule alwaysActiveRule, Rule explicitResetRule, boolean resetAllStagesOnSatisfaction,
            Stage... stages) {
        this(alwaysActiveRule, explicitResetRule, resetAllStagesOnSatisfaction, toStageArrays(stages));
    }

    /**
     * Constructor used by rule serialization for triggered rules without an
     * explicit reset rule.
     *
     * <p>
     * All arrays are defensively copied and must have the same length.
     * </p>
     *
     * @param alwaysActiveRule             the rule evaluated on every bar, or
     *                                     {@code null} to use a permanently false
     *                                     base rule
     * @param resetAllStagesOnSatisfaction true to reset every active stage whenever
     *                                     any delegate is satisfied
     * @param triggerRules                 stage trigger rules
     * @param delegateRules                stage delegate rules
     * @param activationWindowBars         stage activation windows
     * @param primeDelegateWhileInactive   stage delegate priming flags
     * @param resetPolicies                stage reset policies
     * @since 0.22.7
     */
    public TriggeredRule(Rule alwaysActiveRule, boolean resetAllStagesOnSatisfaction, Rule[] triggerRules,
            Rule[] delegateRules, int[] activationWindowBars, boolean[] primeDelegateWhileInactive,
            ResetPolicy[] resetPolicies) {
        this(alwaysActiveRule, null, resetAllStagesOnSatisfaction, triggerRules, delegateRules, activationWindowBars,
                primeDelegateWhileInactive, resetPolicies);
    }

    /**
     * Constructor used by rule serialization and advanced callers that need compact
     * array-based configuration.
     *
     * <p>
     * All arrays are defensively copied and must have the same length.
     * </p>
     *
     * @param alwaysActiveRule             the rule evaluated on every bar, or
     *                                     {@code null} to use a permanently false
     *                                     base rule
     * @param explicitResetRule            the rule that resets
     *                                     {@link ResetPolicy#ON_EXPLICIT_RESET_RULE}
     *                                     stages when satisfied
     * @param resetAllStagesOnSatisfaction true to reset every active stage whenever
     *                                     any delegate is satisfied
     * @param triggerRules                 stage trigger rules
     * @param delegateRules                stage delegate rules
     * @param activationWindowBars         stage activation windows
     * @param primeDelegateWhileInactive   stage delegate priming flags
     * @param resetPolicies                stage reset policies
     * @since 0.22.7
     */
    public TriggeredRule(Rule alwaysActiveRule, Rule explicitResetRule, boolean resetAllStagesOnSatisfaction,
            Rule[] triggerRules, Rule[] delegateRules, int[] activationWindowBars, boolean[] primeDelegateWhileInactive,
            ResetPolicy[] resetPolicies) {
        validateStageArrays(triggerRules, delegateRules, activationWindowBars, primeDelegateWhileInactive,
                resetPolicies);
        this.alwaysActiveRule = alwaysActiveRule != null ? alwaysActiveRule : BooleanRule.FALSE;
        this.explicitResetRule = explicitResetRule;
        this.resetAllStagesOnSatisfaction = resetAllStagesOnSatisfaction;
        this.triggerRules = triggerRules.clone();
        this.delegateRules = delegateRules.clone();
        this.activationWindowBars = activationWindowBars.clone();
        this.primeDelegateWhileInactive = primeDelegateWhileInactive.clone();
        this.resetPolicies = resetPolicies.clone();
        this.hasPositionResetStages = hasResetPolicy(this.resetPolicies, ResetPolicy.ON_POSITION_CHANGE);
        this.hasExplicitResetStages = hasResetPolicy(this.resetPolicies, ResetPolicy.ON_EXPLICIT_RESET_RULE);
        this.stageStates = createStageStates(this.triggerRules.length);
    }

    private TriggeredRule(Rule alwaysActiveRule, Rule explicitResetRule, boolean resetAllStagesOnSatisfaction,
            StageArrays stages) {
        this(alwaysActiveRule, explicitResetRule, resetAllStagesOnSatisfaction, stages.triggerRules(),
                stages.delegateRules(), stages.activationWindowBars(), stages.primeDelegateWhileInactive(),
                stages.resetPolicies());
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        resetStagesOnEvaluationRestart(index, tradingRecord);
        resetStagesOnPositionChange(tradingRecord);
        resetStagesOnExplicitSignal(index, tradingRecord);

        boolean satisfied = alwaysActiveRule.isSatisfied(index, tradingRecord);
        if (!satisfied) {
            satisfied = evaluateStages(index, tradingRecord);
        }

        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private void resetStagesOnEvaluationRestart(int index, TradingRecord tradingRecord) {
        if (evaluationStarted && (tradingRecord != lastTradingRecord || index < lastEvaluatedIndex)) {
            resetAllStages();
            lastPositionSnapshot = null;
        }
        evaluationStarted = true;
        lastTradingRecord = tradingRecord;
        lastEvaluatedIndex = index;
    }

    private boolean evaluateStages(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        for (int stageIndex = 0; stageIndex < triggerRules.length; stageIndex++) {
            StageState state = stageStates[stageIndex];

            if (triggerRules[stageIndex].isSatisfied(index, tradingRecord)) {
                state.activate(index);
            }

            if (!state.active) {
                if (primeDelegateWhileInactive[stageIndex]) {
                    delegateRules[stageIndex].isSatisfied(index, tradingRecord);
                }
                continue;
            }

            if (isExpired(stageIndex, state, index)) {
                state.reset();
                continue;
            }

            if (delegateRules[stageIndex].isSatisfied(index, tradingRecord)) {
                satisfied = true;
                if (!resetAllStagesOnSatisfaction && resetPolicies[stageIndex] == ResetPolicy.ON_SATISFACTION) {
                    state.reset();
                }
            }
        }

        if (satisfied && resetAllStagesOnSatisfaction) {
            resetAllStages();
        }
        return satisfied;
    }

    private void resetStagesOnPositionChange(TradingRecord tradingRecord) {
        if (!hasPositionResetStages) {
            return;
        }
        PositionSnapshot currentPositionSnapshot = PositionSnapshot.from(tradingRecord);
        if (lastPositionSnapshot != null && !lastPositionSnapshot.equals(currentPositionSnapshot)) {
            resetStagesWithPolicy(ResetPolicy.ON_POSITION_CHANGE);
        }
        lastPositionSnapshot = currentPositionSnapshot;
    }

    private void resetStagesOnExplicitSignal(int index, TradingRecord tradingRecord) {
        if (explicitResetRule != null && hasExplicitResetStages
                && explicitResetRule.isSatisfied(index, tradingRecord)) {
            resetStagesWithPolicy(ResetPolicy.ON_EXPLICIT_RESET_RULE);
        }
    }

    private void resetStagesWithPolicy(ResetPolicy resetPolicy) {
        for (int stageIndex = 0; stageIndex < resetPolicies.length; stageIndex++) {
            if (resetPolicies[stageIndex] == resetPolicy) {
                stageStates[stageIndex].reset();
            }
        }
    }

    private void resetAllStages() {
        for (StageState stageState : stageStates) {
            stageState.reset();
        }
    }

    private boolean isExpired(int stageIndex, StageState stageState, int index) {
        return activationWindowBars[stageIndex] != UNBOUNDED_WINDOW
                && index - stageState.activatedAtIndex > activationWindowBars[stageIndex];
    }

    private static StageArrays toStageArrays(Stage... stages) {
        Objects.requireNonNull(stages, "stages");
        return toStageArrays(Arrays.asList(stages));
    }

    private static StageArrays toStageArrays(Collection<Stage> stages) {
        Objects.requireNonNull(stages, "stages");
        Rule[] triggerRules = new Rule[stages.size()];
        Rule[] delegateRules = new Rule[stages.size()];
        int[] activationWindowBars = new int[stages.size()];
        boolean[] primeDelegateWhileInactive = new boolean[stages.size()];
        ResetPolicy[] resetPolicies = new ResetPolicy[stages.size()];

        int index = 0;
        for (Stage stage : stages) {
            Stage checkedStage = Objects.requireNonNull(stage, "stage");
            triggerRules[index] = checkedStage.triggerRule();
            delegateRules[index] = checkedStage.delegateRule();
            activationWindowBars[index] = checkedStage.activationWindowBars();
            primeDelegateWhileInactive[index] = checkedStage.primeDelegateWhileInactive();
            resetPolicies[index] = checkedStage.resetPolicy();
            index++;
        }
        return new StageArrays(triggerRules, delegateRules, activationWindowBars, primeDelegateWhileInactive,
                resetPolicies);
    }

    private static void validateStageArrays(Rule[] triggerRules, Rule[] delegateRules, int[] activationWindowBars,
            boolean[] primeDelegateWhileInactive, ResetPolicy[] resetPolicies) {
        Objects.requireNonNull(triggerRules, "triggerRules");
        Objects.requireNonNull(delegateRules, "delegateRules");
        Objects.requireNonNull(activationWindowBars, "activationWindowBars");
        Objects.requireNonNull(primeDelegateWhileInactive, "primeDelegateWhileInactive");
        Objects.requireNonNull(resetPolicies, "resetPolicies");
        int stageCount = triggerRules.length;
        if (delegateRules.length != stageCount || activationWindowBars.length != stageCount
                || primeDelegateWhileInactive.length != stageCount || resetPolicies.length != stageCount) {
            throw new IllegalArgumentException("stage arrays must have the same length");
        }
        for (int index = 0; index < stageCount; index++) {
            Objects.requireNonNull(triggerRules[index], "triggerRules[" + index + "]");
            Objects.requireNonNull(delegateRules[index], "delegateRules[" + index + "]");
            Objects.requireNonNull(resetPolicies[index], "resetPolicies[" + index + "]");
            if (activationWindowBars[index] < 0) {
                throw new IllegalArgumentException("activationWindowBars[" + index + "] must be >= 0");
            }
        }
    }

    private static boolean hasResetPolicy(ResetPolicy[] resetPolicies, ResetPolicy resetPolicy) {
        for (ResetPolicy candidate : resetPolicies) {
            if (candidate == resetPolicy) {
                return true;
            }
        }
        return false;
    }

    private static StageState[] createStageStates(int stageCount) {
        StageState[] states = new StageState[stageCount];
        for (int index = 0; index < stageCount; index++) {
            states[index] = new StageState();
        }
        return states;
    }

    private record StageArrays(Rule[] triggerRules, Rule[] delegateRules, int[] activationWindowBars,
            boolean[] primeDelegateWhileInactive, ResetPolicy[] resetPolicies) {
    }

    private static final class StageState {
        private boolean active;
        private int activatedAtIndex = Integer.MIN_VALUE;

        private void activate(int index) {
            this.active = true;
            this.activatedAtIndex = index;
        }

        private void reset() {
            this.active = false;
            this.activatedAtIndex = Integer.MIN_VALUE;
        }
    }

    private record PositionSnapshot(boolean recordPresent, int positionCount, boolean positionNew,
            boolean positionOpened, int currentEntryIndex, int lastTradeIndex, String lastTradeType) {

        private static PositionSnapshot from(TradingRecord tradingRecord) {
            if (tradingRecord == null) {
                return new PositionSnapshot(false, 0, false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, "");
            }

            Position currentPosition = tradingRecord.getCurrentPosition();
            Trade lastTrade = tradingRecord.getLastTrade();

            int currentEntryIndex = Integer.MIN_VALUE;
            boolean positionNew = false;
            boolean positionOpened = false;
            if (currentPosition != null) {
                positionNew = currentPosition.isNew();
                positionOpened = currentPosition.isOpened();
                if (currentPosition.getEntry() != null) {
                    currentEntryIndex = currentPosition.getEntry().getIndex();
                }
            }

            int lastTradeIndex = lastTrade != null ? lastTrade.getIndex() : Integer.MIN_VALUE;
            String lastTradeType = lastTrade != null && lastTrade.getType() != null ? lastTrade.getType().name() : "";

            return new PositionSnapshot(true, tradingRecord.getPositionCount(), positionNew, positionOpened,
                    currentEntryIndex, lastTradeIndex, lastTradeType);
        }
    }
}
