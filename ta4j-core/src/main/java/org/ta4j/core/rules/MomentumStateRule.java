/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.MACDVMomentumState;

/**
 * Satisfied when a momentum-state indicator matches the expected state.
 *
 * @since 0.22.3
 */
public class MomentumStateRule extends AbstractRule {

    private final Indicator<MACDVMomentumState> momentumStateIndicator;
    private final MACDVMomentumState expectedState;

    /**
     * Constructor.
     *
     * @param momentumStateIndicator momentum-state indicator
     * @param expectedState          expected momentum state
     * @since 0.22.3
     */
    public MomentumStateRule(Indicator<MACDVMomentumState> momentumStateIndicator, MACDVMomentumState expectedState) {
        this.momentumStateIndicator = Objects.requireNonNull(momentumStateIndicator, "momentumStateIndicator");
        this.expectedState = Objects.requireNonNull(expectedState, "expectedState");
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = expectedState == momentumStateIndicator.getValue(index);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /**
     * @return momentum-state indicator
     * @since 0.22.3
     */
    public Indicator<MACDVMomentumState> getMomentumStateIndicator() {
        return momentumStateIndicator;
    }

    /**
     * @return expected momentum state
     * @since 0.22.3
     */
    public MACDVMomentumState getExpectedState() {
        return expectedState;
    }
}
