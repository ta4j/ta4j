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
 * @since 0.22.4
 */
public enum ElliottLogicProfile {

    /**
     * Classical Elliott constraints with a light hierarchical swing detector.
     */
    ORTHODOX_CLASSICAL("orthodox-classical", "Classical Elliott constraints", 2, PatternSet.all(), false, 0.70),

    /**
     * Broader macro pivot preservation with a wider hierarchical detector.
     */
    HIERARCHICAL_SWING("h1-hierarchical-swing", "Hierarchical swing extraction", 4, PatternSet.all(), false, 0.78),

    /**
     * Impulse-oriented BTC profile that narrows corrective coverage and uses the
     * pattern-aware confidence model.
     */
    BTC_RELAXED_IMPULSE("h2-btc-relaxed-impulse", "Relaxed impulse rules for BTC", 4,
            PatternSet.of(ScenarioType.IMPULSE, ScenarioType.CORRECTIVE_ZIGZAG, ScenarioType.CORRECTIVE_FLAT), true,
            0.82),

    /**
     * Corrective-oriented BTC profile with broader pattern coverage.
     */
    BTC_RELAXED_CORRECTIVE("h3-btc-relaxed-corrective", "Relaxed corrective coverage for BTC", 5, PatternSet.all(),
            true, 0.64),

    /**
     * Anchor-first hybrid profile used by the BTC macro study when start/end span
     * fit matters as much as raw confidence.
     */
    ANCHOR_FIRST_HYBRID("h4-anchor-first-hybrid", "Anchor-first hybrid profile", 5, PatternSet.all(), true, 0.58);

    private final String id;
    private final String title;
    private final int baseFractalWindow;
    private final PatternSet patternSet;
    private final boolean patternAwareConfidence;
    private final double baseConfidenceWeight;

    ElliottLogicProfile(final String id, final String title, final int baseFractalWindow, final PatternSet patternSet,
            final boolean patternAwareConfidence, final double baseConfidenceWeight) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.baseFractalWindow = baseFractalWindow;
        this.patternSet = Objects.requireNonNull(patternSet, "patternSet");
        this.patternAwareConfidence = patternAwareConfidence;
        this.baseConfidenceWeight = baseConfidenceWeight;
    }

    /**
     * @return stable profile identifier used in reports and demos
     * @since 0.22.4
     */
    public String id() {
        return id;
    }

    /**
     * @return human-readable profile title
     * @since 0.22.4
     */
    public String title() {
        return title;
    }

    /**
     * @return base fractal window used by the default hierarchical swing detector
     * @since 0.22.4
     */
    public int baseFractalWindow() {
        return baseFractalWindow;
    }

    /**
     * @return enabled scenario families for the profile
     * @since 0.22.4
     */
    public PatternSet patternSet() {
        return patternSet;
    }

    /**
     * @return {@code true} when the pattern-aware confidence model should be used
     * @since 0.22.4
     */
    public boolean patternAwareConfidence() {
        return patternAwareConfidence;
    }

    /**
     * @return default base-confidence weight used during cross-degree ranking
     * @since 0.22.4
     */
    public double baseConfidenceWeight() {
        return baseConfidenceWeight;
    }
}
