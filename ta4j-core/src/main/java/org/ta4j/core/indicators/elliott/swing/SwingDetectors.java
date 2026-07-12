/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.Arrays;
import java.util.List;

/**
 * Factory helpers for common swing detector configurations.
 *
 * <p>
 * Use these factories to quickly wire up swing detection without manually
 * instantiating each detector or configuration class.
 *
 * @since 0.22.2
 */
public final class SwingDetectors {

    private SwingDetectors() {
    }

    /**
     * Builds a fractal swing detector with symmetric lookback/lookforward.
     *
     * @param window lookback/lookforward window
     * @return fractal swing detector
     * @since 0.22.2
     */
    public static SwingDetector fractal(final int window) {
        return new FractalSwingDetector(window);
    }

    /**
     * Builds a fractal swing detector with explicit window lengths.
     *
     * @param lookbackLength    lookback window
     * @param lookforwardLength lookforward window
     * @param allowedEqualBars  allowed equal-value bars
     * @return fractal swing detector
     * @since 0.22.2
     */
    public static SwingDetector fractal(final int lookbackLength, final int lookforwardLength,
            final int allowedEqualBars) {
        return new FractalSwingDetector(lookbackLength, lookforwardLength, allowedEqualBars);
    }

    /**
     * Builds an adaptive ZigZag swing detector.
     *
     * @param config adaptive config
     * @return adaptive ZigZag detector
     * @since 0.22.2
     */
    public static SwingDetector adaptiveZigZag(final AdaptiveZigZagConfig config) {
        return new AdaptiveZigZagSwingDetector(config);
    }

    /**
     * Builds a causal rolling-slope detector with balanced defaults.
     *
     * @param window bars in each regression window
     * @return slope-change detector
     * @since 0.22.9
     */
    public static SwingDetector slopeChange(final int window) {
        return new SlopeChangeSwingDetector(window);
    }

    /**
     * Builds a causal rolling-slope swing detector.
     *
     * @param config slope-change configuration
     * @return slope-change detector
     * @since 0.22.9
     */
    public static SwingDetector slopeChange(final SlopeChangeConfig config) {
        return new SlopeChangeSwingDetector(config);
    }

    /**
     * Builds a composite detector with the given policy.
     *
     * @param policy    composite policy
     * @param detectors detectors to combine
     * @return composite swing detector
     * @since 0.22.2
     */
    public static SwingDetector composite(final CompositeSwingDetector.Policy policy,
            final SwingDetector... detectors) {
        return new CompositeSwingDetector(policy, toList(detectors));
    }

    /**
     * Builds a tolerant multi-detector consensus.
     *
     * @param indexTolerance maximum bar distance within a pivot cluster
     * @param requiredVotes  detector quorum
     * @param detectors      detectors to combine
     * @return tolerant composite detector
     * @since 0.22.9
     */
    public static SwingDetector consensus(final int indexTolerance, final int requiredVotes,
            final SwingDetector... detectors) {
        return new CompositeSwingDetector(toList(detectors), indexTolerance, requiredVotes);
    }

    private static List<SwingDetector> toList(final SwingDetector... detectors) {
        if (detectors == null || detectors.length == 0) {
            throw new IllegalArgumentException("detectors cannot be null or empty");
        }
        return List.copyOf(Arrays.asList(detectors));
    }
}
