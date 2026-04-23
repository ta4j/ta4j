/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * Blocks new entries for a configurable number of bars after a losing position.
 *
 * <p>
 * The rule inspects the most recent closed position, optionally filters by
 * entry side, and returns {@code false} until the cooldown has elapsed. A reset
 * rule may short-circuit the cooldown early.
 * </p>
 *
 * @since 0.22.7
 */
public class LossTriggeredCooldownRule extends AbstractRule {

    private final Indicator<Num> cooldownBarsIndicator;
    private final Rule resetRule;
    private final TradeType tradeType;

    /**
     * Creates a cooldown rule that applies to any entry side.
     *
     * @param indicator    indicator used only to resolve the bar series
     * @param cooldownBars cooldown length in bars
     * @since 0.22.7
     */
    public LossTriggeredCooldownRule(Indicator<?> indicator, Number cooldownBars) {
        this(indicator, cooldownBars, null, null);
    }

    /**
     * Creates a cooldown rule with an optional reset rule and side filter.
     *
     * @param indicator    indicator used only to resolve the bar series
     * @param cooldownBars cooldown length in bars
     * @param resetRule    optional rule that can clear the cooldown early
     * @param tradeType    optional entry-side filter
     * @since 0.22.7
     */
    public LossTriggeredCooldownRule(Indicator<?> indicator, Number cooldownBars, Rule resetRule, TradeType tradeType) {
        this(new ConstantIndicator<>(Objects.requireNonNull(indicator, "indicator").getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(cooldownBars)), resetRule, tradeType);
    }

    /**
     * Creates a cooldown rule from an explicit cooldown indicator.
     *
     * @param cooldownBarsIndicator indicator supplying the minimum cooldown
     * @param resetRule             optional rule that can clear the cooldown early
     * @param tradeType             optional entry-side filter
     * @since 0.22.7
     */
    public LossTriggeredCooldownRule(Indicator<Num> cooldownBarsIndicator, Rule resetRule, TradeType tradeType) {
        this.cooldownBarsIndicator = Objects.requireNonNull(cooldownBarsIndicator, "cooldownBarsIndicator");
        this.resetRule = resetRule;
        this.tradeType = tradeType;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord == null) {
            traceIsSatisfied(index, true);
            return true;
        }
        Position lastClosedPosition = findLastRelevantClosedPosition(tradingRecord.getPositions());
        if (lastClosedPosition == null || !lastClosedPosition.hasLoss()) {
            traceIsSatisfied(index, true);
            return true;
        }
        if (resetRule != null && resetRule.isSatisfied(index, tradingRecord)) {
            traceIsSatisfied(index, true);
            return true;
        }
        Num cooldownBars = cooldownBarsIndicator.getValue(index);
        Trade exit = lastClosedPosition.getExit();
        if (Num.isNaNOrNull(cooldownBars) || exit == null || index < exit.getIndex()) {
            traceIsSatisfied(index, false);
            return false;
        }
        Num elapsedBars = cooldownBars.getNumFactory().numOf(index - exit.getIndex());
        boolean satisfied = elapsedBars.isGreaterThanOrEqual(cooldownBars);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /**
     * @return the cooldown-bar indicator
     * @since 0.22.7
     */
    public Indicator<Num> getCooldownBarsIndicator() {
        return cooldownBarsIndicator;
    }

    /**
     * @return the optional reset rule
     * @since 0.22.7
     */
    public Rule getResetRule() {
        return resetRule;
    }

    /**
     * @return the optional side filter
     * @since 0.22.7
     */
    public TradeType getTradeType() {
        return tradeType;
    }

    private Position findLastRelevantClosedPosition(List<Position> positions) {
        for (int i = positions.size() - 1; i >= 0; i--) {
            Position position = positions.get(i);
            if (!position.isClosed()) {
                continue;
            }
            Trade entry = position.getEntry();
            if (tradeType != null && (entry == null || entry.getType() != tradeType)) {
                continue;
            }
            return position;
        }
        return null;
    }
}
