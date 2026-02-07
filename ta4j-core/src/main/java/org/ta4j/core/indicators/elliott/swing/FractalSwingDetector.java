/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;

/**
 * Swing detector backed by fractal swing high/low indicators.
 *
 * <p>
 * Use this detector when you prefer classic Bill Williams-style fractal
 * confirmations with configurable lookback/lookforward windows. It is the
 * default choice for deterministic swing detection in Elliott Wave analysis.
 *
 * @since 0.22.2
 */
public final class FractalSwingDetector implements SwingDetector {

    private final int lookbackLength;
    private final int lookforwardLength;
    private final int allowedEqualBars;

    /**
     * Creates a detector with symmetric lookback/lookforward windows.
     *
     * @param window number of bars to inspect before/after a pivot
     * @since 0.22.2
     */
    public FractalSwingDetector(final int window) {
        this(window, window, Math.min(window, window));
    }

    /**
     * Creates a detector with explicit lookback/lookforward windows.
     *
     * @param lookbackLength    bars inspected before a pivot candidate
     * @param lookforwardLength bars inspected after a pivot candidate
     * @param allowedEqualBars  number of equal-value bars allowed on each side
     * @since 0.22.2
     */
    public FractalSwingDetector(final int lookbackLength, final int lookforwardLength, final int allowedEqualBars) {
        if (lookbackLength < 1 || lookforwardLength < 1) {
            throw new IllegalArgumentException("Window lengths must be positive");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be non-negative");
        }
        this.lookbackLength = lookbackLength;
        this.lookforwardLength = lookforwardLength;
        this.allowedEqualBars = allowedEqualBars;
    }

    @Override
    public SwingDetectorResult detect(final BarSeries series, final int index, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
        final ElliottSwingIndicator indicator = new ElliottSwingIndicator(series, lookbackLength, lookforwardLength,
                allowedEqualBars, degree);
        return SwingDetectorResult.fromSwings(indicator.getValue(clampedIndex));
    }

    /**
     * @return lookback window length
     * @since 0.22.2
     */
    public int getLookbackLength() {
        return lookbackLength;
    }

    /**
     * @return lookforward window length
     * @since 0.22.2
     */
    public int getLookforwardLength() {
        return lookforwardLength;
    }

    /**
     * @return allowed equal bars for flat tops/bottoms
     * @since 0.22.2
     */
    public int getAllowedEqualBars() {
        return allowedEqualBars;
    }
}
