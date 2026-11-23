/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;

import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.rules.RuleCopies;

/**
 * Package-local strategy snapshot support shared by backtest result and sizing
 * carriers that expose {@link Strategy} references.
 */
final class StrategySnapshots {

    private StrategySnapshots() {
    }

    static Strategy copy(Strategy strategy) {
        Strategy source = Objects.requireNonNull(strategy, "strategy");
        if (source.getClass() != BaseStrategy.class) {
            // Rebuilding a subclass or custom Strategy as a BaseStrategy would drop
            // its overridden entry/exit/operate behavior, so keep the original.
            return source;
        }
        return new BaseStrategy(source.getName(), RuleCopies.copy(source.getEntryRule()),
                RuleCopies.copy(source.getExitRule()), source.getUnstableBars(), source.getStartingType());
    }
}
