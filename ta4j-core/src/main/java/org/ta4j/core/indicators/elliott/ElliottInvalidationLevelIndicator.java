/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Calculates the invalidation price level for Elliott wave scenarios.
 *
 * <p>
 * Returns the price that, if breached, would invalidate the primary wave count.
 * Invalidation levels follow Elliott wave rules:
 * <ul>
 * <li>Wave 2 cannot retrace beyond the start of Wave 1</li>
 * <li>Wave 4 cannot enter Wave 1 territory (overlap rule)</li>
 * <li>Wave 3 cannot be the shortest impulse wave</li>
 * </ul>
 *
 * <p>
 * Use this indicator when you need actionable price levels for stops or alerts.
 * Choose {@link InvalidationMode} to control whether levels come from the
 * primary scenario only, the tightest high-confidence level, or the widest
 * aggressive view.
 *
 * @since 0.22.0
 */
public class ElliottInvalidationLevelIndicator extends CachedIndicator<Num> {

    private final ElliottScenarioIndicator scenarioIndicator;
    private final InvalidationMode mode;

    /**
     * Creates an invalidation indicator using primary scenario levels.
     *
     * @param scenarioIndicator source of scenario data
     * @since 0.22.0
     */
    public ElliottInvalidationLevelIndicator(final ElliottScenarioIndicator scenarioIndicator) {
        this(scenarioIndicator, InvalidationMode.PRIMARY);
    }

    /**
     * Creates an invalidation indicator with a specific mode.
     *
     * @param scenarioIndicator source of scenario data
     * @param mode              which invalidation level to use
     * @since 0.22.0
     */
    public ElliottInvalidationLevelIndicator(final ElliottScenarioIndicator scenarioIndicator,
            final InvalidationMode mode) {
        super(requireSeries(scenarioIndicator));
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    private static BarSeries requireSeries(final ElliottScenarioIndicator scenarioIndicator) {
        final BarSeries series = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator").getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Scenario indicator must expose a backing series");
        }
        return series;
    }

    @Override
    protected Num calculate(final int index) {
        final ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(index);

        return switch (mode) {
        case PRIMARY -> calculatePrimaryInvalidation(scenarioSet);
        case CONSERVATIVE -> calculateConservativeInvalidation(scenarioSet);
        case AGGRESSIVE -> calculateAggressiveInvalidation(scenarioSet);
        };
    }

    @Override
    public int getCountOfUnstableBars() {
        return scenarioIndicator.getCountOfUnstableBars();
    }

    private Num calculatePrimaryInvalidation(final ElliottScenarioSet scenarioSet) {
        return scenarioSet.base().map(ElliottScenario::invalidationPrice).orElse(NaN);
    }

    private Num calculateConservativeInvalidation(final ElliottScenarioSet scenarioSet) {
        // Use the most conservative (tightest) invalidation across all high-confidence
        // scenarios
        final List<ElliottScenario> scenarios = scenarioSet.all();
        if (scenarios.isEmpty()) {
            return NaN;
        }

        Num conservative = null;
        Boolean bullish = null;

        for (final ElliottScenario scenario : scenarios) {
            if (!scenario.isHighConfidence()) {
                continue;
            }

            final Num invalidation = scenario.invalidationPrice();
            if (Num.isNaNOrNull(invalidation)) {
                continue;
            }

            // Skip scenarios without a known direction
            if (!scenario.hasKnownDirection()) {
                continue;
            }

            if (conservative == null) {
                conservative = invalidation;
                bullish = scenario.isBullish();
            } else {
                // For bullish, conservative = highest invalidation (tightest stop)
                // For bearish, conservative = lowest invalidation (tightest stop)
                if (bullish != null && bullish) {
                    conservative = conservative.max(invalidation);
                } else {
                    conservative = conservative.min(invalidation);
                }
            }
        }

        return conservative != null ? conservative : NaN;
    }

    private Num calculateAggressiveInvalidation(final ElliottScenarioSet scenarioSet) {
        // Use the most aggressive (widest) invalidation - only invalidate if ALL
        // scenarios would be invalid
        final List<ElliottScenario> scenarios = scenarioSet.all();
        if (scenarios.isEmpty()) {
            return NaN;
        }

        Num aggressive = null;
        Boolean bullish = null;

        for (final ElliottScenario scenario : scenarios) {
            final Num invalidation = scenario.invalidationPrice();
            if (Num.isNaNOrNull(invalidation)) {
                continue;
            }

            // Skip scenarios without a known direction
            if (!scenario.hasKnownDirection()) {
                continue;
            }

            if (aggressive == null) {
                aggressive = invalidation;
                bullish = scenario.isBullish();
            } else {
                // For bullish, aggressive = lowest invalidation (widest stop)
                // For bearish, aggressive = highest invalidation (widest stop)
                if (bullish != null && bullish) {
                    aggressive = aggressive.min(invalidation);
                } else {
                    aggressive = aggressive.max(invalidation);
                }
            }
        }

        return aggressive != null ? aggressive : NaN;
    }

    /**
     * Checks whether the given price would invalidate the primary scenario.
     *
     * @param index bar index
     * @param price price to test
     * @return {@code true} if the price invalidates the primary scenario; returns
     *         {@code false} if no primary scenario exists or if its direction is
     *         unknown
     * @since 0.22.0
     */
    public boolean isInvalidated(final int index, final Num price) {
        final Optional<ElliottScenario> primary = scenarioIndicator.primaryScenario(index);
        return primary.filter(ElliottScenario::hasKnownDirection)
                .map(scenario -> scenario.isInvalidatedBy(price))
                .orElse(false);
    }

    /**
     * Gets the distance from current price to invalidation level.
     *
     * @param index        bar index
     * @param currentPrice current price
     * @return distance to invalidation (positive = still valid); returns NaN if no
     *         primary scenario exists or if its direction is unknown
     * @since 0.22.0
     */
    public Num distanceToInvalidation(final int index, final Num currentPrice) {
        final Num invalidation = getValue(index);
        if (Num.isNaNOrNull(invalidation) || Num.isNaNOrNull(currentPrice)) {
            return NaN;
        }

        final Optional<ElliottScenario> primary = scenarioIndicator.primaryScenario(index);
        if (primary.isEmpty() || !primary.get().hasKnownDirection()) {
            return NaN;
        }

        // For bullish, distance = current - invalidation (positive if above)
        // For bearish, distance = invalidation - current (positive if below)
        if (primary.get().isBullish()) {
            return currentPrice.minus(invalidation);
        } else {
            return invalidation.minus(currentPrice);
        }
    }

    /**
     * Mode for selecting which invalidation level to use.
     *
     * @since 0.22.0
     */
    public enum InvalidationMode {
        /**
         * Use the primary scenario's invalidation level.
         */
        PRIMARY,

        /**
         * Use the most conservative (tightest) invalidation across high-confidence
         * scenarios.
         */
        CONSERVATIVE,

        /**
         * Use the most aggressive (widest) invalidation - price must invalidate ALL
         * scenarios.
         */
        AGGRESSIVE
    }
}
