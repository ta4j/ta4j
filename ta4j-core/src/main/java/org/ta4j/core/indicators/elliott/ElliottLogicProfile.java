/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.Objects;

/**
 * Core Elliott analysis logic profiles.
 *
 * <p>
 * These profiles package the macro-study learnings into reusable core runner
 * defaults without hard-coding BTC-specific anchors or truth sets into
 * {@code ta4j-core}. A profile tunes three generic analysis levers:
 * <ul>
 * <li>the hierarchical swing-detector sensitivity</li>
 * <li>the enabled scenario families</li>
 * <li>the confidence-model style used during ranking</li>
 * </ul>
 *
 * <p>
 * Use
 * {@link org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner.Builder#logicProfile(ElliottLogicProfile)}
 * to apply one of these presets while still retaining the ability to override
 * individual builder settings afterwards.
 *
 * @since 0.22.7
 */
public enum ElliottLogicProfile {

    /**
     * Classical Elliott constraints with a light hierarchical swing detector.
     */
    ORTHODOX_CLASSICAL("orthodox-classical", "Classical Elliott constraints", 2, 1, 1, PatternSet.all(), false, 0.70,
            25, 0, 0.74),

    /**
     * Broader macro pivot preservation with a wider hierarchical detector.
     */
    HIERARCHICAL_SWING("h1-hierarchical-swing", "Hierarchical swing extraction", 4, 1, 1, PatternSet.all(), false, 0.78,
            25, 0, 0.72),

    /**
     * Impulse-oriented profile that narrows corrective coverage and uses the
     * pattern-aware confidence model.
     */
    BTC_RELAXED_IMPULSE("h2-pattern-aware-impulse", "Pattern-aware impulse emphasis", 4, 1, 1,
            PatternSet.of(ScenarioType.IMPULSE, ScenarioType.CORRECTIVE_ZIGZAG, ScenarioType.CORRECTIVE_FLAT), true,
            0.82, 35, 0, 0.70),

    /**
     * Corrective-oriented profile with broader pattern coverage.
     */
    BTC_RELAXED_CORRECTIVE("h3-pattern-aware-corrective", "Pattern-aware corrective breadth", 5, 1, 1, PatternSet.all(),
            true, 0.64, 35, 0, 0.68),

    /**
     * Span-aware hybrid profile used when start/end fit matters as much as raw
     * confidence.
     */
    ANCHOR_FIRST_HYBRID("h4-span-aware-hybrid", "Span-aware hybrid scoring", 5, 2, 2, PatternSet.all(), true, 0.58, 40,
            0, 0.66);

    private final String id;
    private final String title;
    private final int baseFractalWindow;
    private final int higherDegrees;
    private final int lowerDegrees;
    private final PatternSet patternSet;
    private final boolean patternAwareConfidence;
    private final double baseConfidenceWeight;
    private final int maxScenarios;
    private final int scenarioSwingWindow;
    private final double acceptanceThreshold;

    ElliottLogicProfile(final String id, final String title, final int baseFractalWindow, final int higherDegrees,
            final int lowerDegrees, final PatternSet patternSet, final boolean patternAwareConfidence,
            final double baseConfidenceWeight, final int maxScenarios, final int scenarioSwingWindow,
            final double acceptanceThreshold) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.baseFractalWindow = baseFractalWindow;
        this.higherDegrees = higherDegrees;
        this.lowerDegrees = lowerDegrees;
        this.patternSet = Objects.requireNonNull(patternSet, "patternSet");
        this.patternAwareConfidence = patternAwareConfidence;
        this.baseConfidenceWeight = baseConfidenceWeight;
        this.maxScenarios = maxScenarios;
        this.scenarioSwingWindow = scenarioSwingWindow;
        this.acceptanceThreshold = acceptanceThreshold;
    }

    /**
     * @return stable profile identifier used in reports and demos
     * @since 0.22.7
     */
    public String id() {
        return id;
    }

    /**
     * @return human-readable profile title
     * @since 0.22.7
     */
    public String title() {
        return title;
    }

    /**
     * @return base fractal window used by the default hierarchical swing detector
     * @since 0.22.7
     */
    public int baseFractalWindow() {
        return baseFractalWindow;
    }

    /**
     * @return default number of higher-degree confirmations to include
     * @since 0.22.7
     */
    public int higherDegrees() {
        return higherDegrees;
    }

    /**
     * @return default number of lower-degree confirmations to include
     * @since 0.22.7
     */
    public int lowerDegrees() {
        return lowerDegrees;
    }

    /**
     * @return enabled scenario families for the profile
     * @since 0.22.7
     */
    public PatternSet patternSet() {
        return patternSet;
    }

    /**
     * @return {@code true} when the pattern-aware confidence model should be used
     * @since 0.22.7
     */
    public boolean patternAwareConfidence() {
        return patternAwareConfidence;
    }

    /**
     * @return default base-confidence weight used during cross-degree ranking
     * @since 0.22.7
     */
    public double baseConfidenceWeight() {
        return baseConfidenceWeight;
    }

    /**
     * @return default maximum number of ranked scenarios retained by the runner
     * @since 0.22.7
     */
    public int maxScenarios() {
        return maxScenarios;
    }

    /**
     * @return default swing window forwarded to scenario generation
     * @since 0.22.7
     */
    public int scenarioSwingWindow() {
        return scenarioSwingWindow;
    }

    /**
     * @return default anchored-fit acceptance threshold for macro profile studies
     * @since 0.22.7
     */
    public double acceptanceThreshold() {
        return acceptanceThreshold;
    }
}
