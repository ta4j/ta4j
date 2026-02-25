/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.macd.MACDVMomentumState;

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

    /**
     * Constructor for legacy momentum-state enum support.
     *
     * @param momentumStateIndicator momentum-state indicator
     * @param expectedState          expected momentum state
     * @since 0.22.3
     * @deprecated use
     *             {@link #MomentumStateRule(Indicator, org.ta4j.core.indicators.macd.MACDVMomentumState)}
     */
    @Deprecated(since = "0.22.3", forRemoval = false)
    @SuppressWarnings("deprecation")
    public MomentumStateRule(Indicator<org.ta4j.core.indicators.MACDVMomentumState> momentumStateIndicator,
            org.ta4j.core.indicators.MACDVMomentumState expectedState) {
        this(adaptLegacyMomentumStateIndicator(momentumStateIndicator),
                toMomentumState(Objects.requireNonNull(expectedState, "expectedState")));
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

    @SuppressWarnings("deprecation")
    private static Indicator<MACDVMomentumState> adaptLegacyMomentumStateIndicator(
            Indicator<org.ta4j.core.indicators.MACDVMomentumState> legacyMomentumStateIndicator) {
        Indicator<org.ta4j.core.indicators.MACDVMomentumState> validatedIndicator = Objects
                .requireNonNull(legacyMomentumStateIndicator, "momentumStateIndicator");
        return new Indicator<>() {
            @Override
            public MACDVMomentumState getValue(int index) {
                return toMomentumState(validatedIndicator.getValue(index));
            }

            @Override
            public int getCountOfUnstableBars() {
                return validatedIndicator.getCountOfUnstableBars();
            }

            @Override
            public BarSeries getBarSeries() {
                return validatedIndicator.getBarSeries();
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static MACDVMomentumState toMomentumState(org.ta4j.core.indicators.MACDVMomentumState legacyState) {
        if (legacyState == null) {
            return MACDVMomentumState.UNDEFINED;
        }
        return MACDVMomentumState.valueOf(legacyState.name());
    }
}
