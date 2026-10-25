/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.num.Num;

/**
 * Deterministic return and moment-state sources shared by the Monte Carlo
 * forecast tests.
 */
final class MonteCarloTestFixtures {

    private MonteCarloTestFixtures() {
    }

    /** Fixed log or decimal returns with a configurable unstable prefix. */
    static final class FixedReturnIndicator extends FixedIndicator<Num> implements ReturnIndicator {

        private final ReturnRepresentation representation;
        private final int unstableBars;

        FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, Num... values) {
            this(series, representation, 0, values);
        }

        FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, int unstableBars, Num... values) {
            super(series, values);
            this.representation = representation;
            this.unstableBars = unstableBars;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }

    /** Always-stable zero-moment state over a return source. */
    static final class FixedReturnStateIndicator implements ReturnForecastStateIndicator<ReturnForecastState> {

        private final ReturnIndicator returns;
        private final ReturnRepresentation representation;

        FixedReturnStateIndicator(ReturnIndicator returns, ReturnRepresentation representation) {
            this.returns = returns;
            this.representation = representation;
        }

        @Override
        public ReturnIndicator getReturnIndicator() {
            return returns;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }

        @Override
        public ReturnForecastState getValue(int index) {
            Num zero = getBarSeries().numFactory().zero();
            return ReturnForecastState.stable(index, index + 1, representation, zero, zero, zero);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return returns.getBarSeries();
        }
    }
}
