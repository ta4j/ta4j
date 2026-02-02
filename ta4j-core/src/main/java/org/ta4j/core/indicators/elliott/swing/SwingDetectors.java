/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.Arrays;
import java.util.List;

/**
 * Factory helpers for common swing detector configurations.
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

    private static List<SwingDetector> toList(final SwingDetector... detectors) {
        if (detectors == null || detectors.length == 0) {
            throw new IllegalArgumentException("detectors cannot be null or empty");
        }
        return List.copyOf(Arrays.asList(detectors));
    }
}
