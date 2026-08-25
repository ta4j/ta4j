/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.Objects;

import org.ta4j.core.analysis.AnalysisContext;

/**
 * Immutable configuration for {@link EventMutualInformationEvaluator}.
 *
 * <p>
 * A predictor sample at index {@code i} is labeled positive when at least one
 * target event occurs in the bar window
 * {@code [i + targetWindowStartBars, i + targetWindowEndBars]}. Both offsets
 * are non-negative and the window is inclusive, so {@code (0, 0)} labels the
 * current bar and {@code (1, 3)} labels the next three bars. Every target index
 * must lie inside the effective evaluation range; samples whose target window
 * would cross the range boundary are excluded.
 * </p>
 *
 * @param targetWindowStartBars inclusive lower bound of the target window,
 *                              {@code >= 0}
 * @param targetWindowEndBars   inclusive upper bound of the target window,
 *                              {@code >= targetWindowStartBars}
 * @param predictorBinCount     requested predictor bin count, at least 2 and at
 *                              most {@link #MAX_PREDICTOR_BIN_COUNT}
 * @param binningStrategy       predictor discretization
 * @param historyPolicy         how to treat requested history outside the
 *                              available range;
 *                              {@link AnalysisContext.MissingHistoryPolicy#CLAMP}
 *                              is the default
 * @since 0.24.2
 */
public record EventMutualInformationConfig(int targetWindowStartBars, int targetWindowEndBars, int predictorBinCount,
        BinningStrategy binningStrategy, AnalysisContext.MissingHistoryPolicy historyPolicy) {

    /**
     * Maximum predictor bin count. Bounds the joint-count arrays to a fixed few
     * megabytes per evaluation and keeps {@code effectiveBinCount * 2} free of
     * integer overflow.
     */
    public static final int MAX_PREDICTOR_BIN_COUNT = 1_000_000;

    /**
     * Creates a config whose missing history is clamped to the available range
     * ({@link AnalysisContext.MissingHistoryPolicy#CLAMP}).
     *
     * @param targetWindowStartBars inclusive lower bound of the target window
     * @param targetWindowEndBars   inclusive upper bound of the target window
     * @param predictorBinCount     requested predictor bin count
     * @param binningStrategy       predictor discretization
     * @since 0.24.2
     */
    public EventMutualInformationConfig(int targetWindowStartBars, int targetWindowEndBars, int predictorBinCount,
            BinningStrategy binningStrategy) {
        this(targetWindowStartBars, targetWindowEndBars, predictorBinCount, binningStrategy,
                AnalysisContext.MissingHistoryPolicy.CLAMP);
    }

    /**
     * Validates the configuration.
     *
     * @throws IllegalArgumentException if {@code targetWindowStartBars < 0},
     *                                  {@code targetWindowEndBars <
     *                                  targetWindowStartBars}, or
     *                                  {@code predictorBinCount < 2} or
     *                                  {@code predictorBinCount >
     *                                  MAX_PREDICTOR_BIN_COUNT}
     * @throws NullPointerException     if {@code binningStrategy} or
     *                                  {@code historyPolicy} is null
     */
    public EventMutualInformationConfig {
        if (targetWindowStartBars < 0) {
            throw new IllegalArgumentException("targetWindowStartBars must be >= 0");
        }
        if (targetWindowEndBars < targetWindowStartBars) {
            throw new IllegalArgumentException("targetWindowEndBars must be >= targetWindowStartBars");
        }
        if (predictorBinCount < 2) {
            throw new IllegalArgumentException("predictorBinCount must be >= 2");
        }
        if (predictorBinCount > MAX_PREDICTOR_BIN_COUNT) {
            throw new IllegalArgumentException("predictorBinCount must be <= " + MAX_PREDICTOR_BIN_COUNT);
        }
        Objects.requireNonNull(binningStrategy, "binningStrategy");
        Objects.requireNonNull(historyPolicy, "historyPolicy");
    }
}
