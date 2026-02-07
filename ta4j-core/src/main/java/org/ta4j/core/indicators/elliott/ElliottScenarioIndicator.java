/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator that produces a set of ranked Elliott wave scenarios for each bar.
 *
 * <p>
 * Unlike {@link ElliottPhaseIndicator} which returns a single deterministic
 * phase, this indicator generates multiple alternative interpretations ranked
 * by confidence score.
 *
 * <p>
 * Use this indicator when you need to reason about ambiguity or rank multiple
 * possible wave counts per bar. It is a core building block for
 * {@link ElliottWaveFacade} and {@link ElliottTrendBiasIndicator}.
 *
 * @since 0.22.0
 */
public class ElliottScenarioIndicator extends CachedIndicator<ElliottScenarioSet> {

    private static final int SCENARIO_SWING_WINDOW = 5;

    private final ElliottSwingIndicator swingIndicator;
    private final ElliottChannelIndicator channelIndicator;
    private final ElliottScenarioGenerator generator;
    private final ElliottDegree degree;

    /**
     * Creates a scenario indicator with default settings.
     *
     * @param swingIndicator source of swing data
     * @since 0.22.0
     */
    public ElliottScenarioIndicator(final ElliottSwingIndicator swingIndicator) {
        this(swingIndicator, new ElliottChannelIndicator(swingIndicator));
    }

    /**
     * Creates a scenario indicator with a custom channel indicator.
     *
     * @param swingIndicator   source of swing data
     * @param channelIndicator channel for confluence scoring
     * @since 0.22.0
     */
    public ElliottScenarioIndicator(final ElliottSwingIndicator swingIndicator,
            final ElliottChannelIndicator channelIndicator) {
        this(swingIndicator, channelIndicator,
                new ElliottScenarioGenerator(requireSeries(swingIndicator).numFactory()));
    }

    /**
     * Creates a scenario indicator with a custom generator.
     *
     * @param swingIndicator   source of swing data
     * @param channelIndicator channel for confluence scoring
     * @param generator        scenario generator
     * @since 0.22.0
     */
    public ElliottScenarioIndicator(final ElliottSwingIndicator swingIndicator,
            final ElliottChannelIndicator channelIndicator, final ElliottScenarioGenerator generator) {
        super(requireSeries(swingIndicator));
        this.swingIndicator = Objects.requireNonNull(swingIndicator, "swingIndicator");
        this.channelIndicator = Objects.requireNonNull(channelIndicator, "channelIndicator");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.degree = swingIndicator.getDegree();
    }

    private static BarSeries requireSeries(final ElliottSwingIndicator swingIndicator) {
        final BarSeries series = Objects.requireNonNull(swingIndicator, "swingIndicator").getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Swing indicator must expose a backing series");
        }
        return series;
    }

    @Override
    protected ElliottScenarioSet calculate(final int index) {
        final List<ElliottSwing> swings = swingIndicator.getValue(index);
        if (swings.isEmpty()) {
            return ElliottScenarioSet.empty(index);
        }

        // Scenario generation inspects leading swings; keep the window aligned to the
        // latest structure.
        final List<ElliottSwing> recentSwings = recentSwings(swings);
        if (recentSwings.isEmpty()) {
            return ElliottScenarioSet.empty(index);
        }

        final ElliottChannel channel = channelIndicator.getValue(index);
        return generator.generate(recentSwings, degree, channel, index);
    }

    private List<ElliottSwing> recentSwings(final List<ElliottSwing> swings) {
        if (swings.size() <= SCENARIO_SWING_WINDOW) {
            return swings;
        }
        return List.copyOf(swings.subList(swings.size() - SCENARIO_SWING_WINDOW, swings.size()));
    }

    @Override
    public int getCountOfUnstableBars() {
        return swingIndicator.getCountOfUnstableBars();
    }

    /**
     * Convenience method to get the base case (highest confidence) scenario.
     *
     * @param index bar index
     * @return base case scenario, or empty if no scenarios exist
     * @since 0.22.0
     */
    public Optional<ElliottScenario> primaryScenario(final int index) {
        return getValue(index).base();
    }

    /**
     * Convenience method to get alternative scenarios (excluding base case).
     *
     * @param index bar index
     * @return list of alternative scenarios sorted by confidence
     * @since 0.22.0
     */
    public List<ElliottScenario> alternatives(final int index) {
        return getValue(index).alternatives();
    }

    /**
     * Gets the consensus phase if all high-confidence scenarios agree.
     *
     * @param index bar index
     * @return consensus phase, or NONE if no consensus
     * @since 0.22.0
     */
    public ElliottPhase consensus(final int index) {
        return getValue(index).consensus();
    }

    /**
     * Checks whether there is strong consensus at the given index.
     *
     * @param index bar index
     * @return {@code true} if scenarios show strong consensus
     * @since 0.22.0
     */
    public boolean hasStrongConsensus(final int index) {
        return getValue(index).hasStrongConsensus();
    }

    /**
     * Gets the confidence score of the primary scenario.
     *
     * @param index bar index
     * @return confidence score (0.0 - 1.0), or 0.0 if no scenarios
     * @since 0.22.0
     */
    public Num primaryConfidence(final int index) {
        return primaryScenario(index).map(ElliottScenario::confidenceScore).orElse(getBarSeries().numFactory().zero());
    }

    /**
     * Gets the spread between primary and secondary scenario confidence.
     *
     * @param index bar index
     * @return confidence spread
     * @since 0.22.0
     */
    public double confidenceSpread(final int index) {
        return getValue(index).confidenceSpread();
    }

    /**
     * @return the underlying swing indicator
     * @since 0.22.0
     */
    public ElliottSwingIndicator getSwingIndicator() {
        return swingIndicator;
    }

    /**
     * @return the underlying channel indicator
     * @since 0.22.0
     */
    public ElliottChannelIndicator getChannelIndicator() {
        return channelIndicator;
    }
}
