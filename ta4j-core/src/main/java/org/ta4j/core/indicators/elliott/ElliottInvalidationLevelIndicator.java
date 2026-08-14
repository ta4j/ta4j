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
        this(validatedConfig(scenarioIndicator, InvalidationMode.PRIMARY));
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
        this(validatedConfig(scenarioIndicator, mode));
    }

    private ElliottInvalidationLevelIndicator(final Config config) {
        super(config.series());
        this.scenarioIndicator = config.scenarioIndicator();
        this.mode = config.mode();
    }

    private static Config validatedConfig(final ElliottScenarioIndicator scenarioIndicator,
            final InvalidationMode mode) {
        final BarSeries series = requireSeries(scenarioIndicator);
        final ElliottScenarioIndicator validatedScenarioIndicator = Objects
                .requireNonNull(scenarioIndicator, "scenarioIndicator")
                .copy();
        final InvalidationMode validatedMode = Objects.requireNonNull(mode, "mode");
        return new Config(series, validatedScenarioIndicator, validatedMode);
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
        case CONSERVATIVE -> calculateConservativeInvalidation(scenarioSet, index);
        case AGGRESSIVE -> calculateAggressiveInvalidation(scenarioSet, index);
        };
    }

    @Override
    public int getCountOfUnstableBars() {
        return scenarioIndicator.getCountOfUnstableBars();
    }

    private Num calculatePrimaryInvalidation(final ElliottScenarioSet scenarioSet) {
        return scenarioSet.base().map(ElliottScenario::invalidationPrice).orElse(NaN);
    }

    /**
     * Computes the conservative (tightest) invalidation level across all
     * high-confidence scenarios.
     *
     * <p>
     * Bullish and bearish scenarios are folded independently (bullish: highest
     * invalidation; bearish: lowest), then the aggregate closest to the close price
     * at {@code index} is selected. When only one direction is present, its
     * aggregate is returned directly.
     *
     * @param scenarioSet scenarios to fold
     * @param index       bar index providing the close-price reference
     * @return the tightest invalidation level, or NaN when no scenario has a usable
     *         invalidation
     * @since 0.24.2
     */
    Num calculateConservativeInvalidation(final ElliottScenarioSet scenarioSet, final int index) {
        final List<ElliottScenario> scenarios = scenarioSet.all();
        if (scenarios.isEmpty()) {
            return NaN;
        }

        final Num reference = getBarSeries().getBar(index).getClosePrice();
        return selectAcrossDirections(foldDirectionalInvalidations(scenarios, true, true),
                foldDirectionalInvalidations(scenarios, false, true), reference, false);
    }

    /**
     * Computes the aggressive (widest) invalidation level across all scenarios.
     *
     * <p>
     * Bullish and bearish scenarios are folded independently (bullish: lowest
     * invalidation; bearish: highest), then the aggregate farthest from the close
     * price at {@code index} is selected. When only one direction is present, its
     * aggregate is returned directly.
     *
     * @param scenarioSet scenarios to fold
     * @param index       bar index providing the close-price reference
     * @return the widest invalidation level, or NaN when no scenario has a usable
     *         invalidation
     * @since 0.24.2
     */
    Num calculateAggressiveInvalidation(final ElliottScenarioSet scenarioSet, final int index) {
        final List<ElliottScenario> scenarios = scenarioSet.all();
        if (scenarios.isEmpty()) {
            return NaN;
        }

        final Num reference = getBarSeries().getBar(index).getClosePrice();
        return selectAcrossDirections(foldDirectionalInvalidations(scenarios, true, false),
                foldDirectionalInvalidations(scenarios, false, false), reference, true);
    }

    /**
     * Folds the invalidation levels of scenarios in one direction.
     *
     * <p>
     * For a conservative fold the returned level is the tightest stop for that
     * direction (bullish: highest invalidation, bearish: lowest); for an aggressive
     * fold it is the widest (bullish: lowest, bearish: highest). Conservative folds
     * additionally require high-confidence scenarios. Scenarios without a known
     * direction or with a NaN invalidation are skipped.
     *
     * @param scenarios        scenarios to fold
     * @param bullishDirection {@code true} to fold bullish scenarios, {@code false}
     *                         for bearish
     * @param conservative     {@code true} for a conservative (tightest) fold,
     *                         {@code false} for an aggressive (widest) fold
     * @return the folded invalidation level, or {@code null} when no scenario
     *         matched
     * @since 0.24.2
     */
    static Num foldDirectionalInvalidations(final List<ElliottScenario> scenarios, final boolean bullishDirection,
            final boolean conservative) {
        Num folded = null;
        for (final ElliottScenario scenario : scenarios) {
            if (conservative && !scenario.isHighConfidence()) {
                continue;
            }
            if (!scenario.hasKnownDirection() || scenario.isBullish() != bullishDirection) {
                continue;
            }
            final Num invalidation = scenario.invalidationPrice();
            if (Num.isNaNOrNull(invalidation)) {
                continue;
            }

            final boolean takeMax = bullishDirection == conservative;
            folded = folded == null ? invalidation : takeMax ? folded.max(invalidation) : folded.min(invalidation);
        }
        return folded;
    }

    /**
     * Selects one of two directional invalidation levels using the distance to a
     * price reference.
     *
     * <p>
     * When both levels are present, the level closest to the reference is returned
     * for tightest selection and the farthest for widest selection. Equal
     * distances, or an unusable (NaN/null) reference, resolve to the bullish level
     * for deterministic behavior.
     *
     * @param bullish      bullish invalidation level, may be {@code null}
     * @param bearish      bearish invalidation level, may be {@code null}
     * @param reference    price used to rank distances
     * @param preferWidest {@code true} to prefer the farthest level, {@code false}
     *                     for the closest
     * @return the selected level, or NaN when both levels are {@code null}
     * @since 0.24.2
     */
    static Num selectAcrossDirections(final Num bullish, final Num bearish, final Num reference,
            final boolean preferWidest) {
        if (bullish == null && bearish == null) {
            return NaN;
        }
        if (bullish == null) {
            return bearish;
        }
        if (bearish == null) {
            return bullish;
        }
        if (Num.isNaNOrNull(reference)) {
            return bullish;
        }

        final Num bullishDistance = bullish.minus(reference).abs();
        final Num bearishDistance = bearish.minus(reference).abs();
        if (preferWidest) {
            return bearishDistance.isGreaterThan(bullishDistance) ? bearish : bullish;
        }
        return bullishDistance.isGreaterThan(bearishDistance) ? bearish : bullish;
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

        // Primary mode follows the primary scenario's direction. The other modes
        // fold levels across scenarios, so the sign follows the selected level's
        // position relative to the current price instead.
        final boolean bullish = mode == InvalidationMode.PRIMARY ? primary.get().isBullish()
                : currentPrice.isGreaterThan(invalidation);

        // For bullish, distance = current - invalidation (positive if above)
        // For bearish, distance = invalidation - current (positive if below)
        if (bullish) {
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

    ElliottInvalidationLevelIndicator copy() {
        return new ElliottInvalidationLevelIndicator(new Config(getBarSeries(), scenarioIndicator.copy(), mode));
    }

    private record Config(BarSeries series, ElliottScenarioIndicator scenarioIndicator, InvalidationMode mode) {
    }
}
