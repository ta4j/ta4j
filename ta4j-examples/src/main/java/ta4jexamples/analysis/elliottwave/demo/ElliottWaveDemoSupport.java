/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.demo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;

import java.time.Duration;
import java.util.List;

/**
 * Shared support helpers for the Elliott Wave demo entry points.
 * <p>
 * Holds the degree auto-selection logic so every demo defaults to the same
 * recommendation without depending on the suite demo's public surface.
 *
 * @since 0.24.2
 */
public final class ElliottWaveDemoSupport {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveDemoSupport.class);

    /** Default Elliott wave degree used when auto selection fails. */
    private static final ElliottDegree DEFAULT_DEGREE = ElliottDegree.PRIMARY;

    private ElliottWaveDemoSupport() {
    }

    /**
     * Auto-selects an Elliott degree from the bar series duration and history size.
     *
     * @param series bar series used for recommendation
     * @return recommended degree, or {@link #DEFAULT_DEGREE} when recommendation
     *         cannot be derived
     * @since 0.24.2
     */
    public static ElliottDegree autoSelectDegree(BarSeries series) {
        return selectRecommendedDegree(series);
    }

    private static ElliottDegree selectRecommendedDegree(BarSeries series) {
        if (series == null || series.isEmpty()) {
            LOG.warn("Series unavailable for degree selection, using default: {}", DEFAULT_DEGREE);
            return DEFAULT_DEGREE;
        }

        Duration barDuration = series.getFirstBar().getTimePeriod();
        if (barDuration == null || barDuration.isZero() || barDuration.isNegative()) {
            LOG.warn("Invalid bar duration '{}' for degree selection, using default: {}", barDuration, DEFAULT_DEGREE);
            return DEFAULT_DEGREE;
        }

        int barCount = series.getBarCount();
        try {
            List<ElliottDegree> recommendations = ElliottDegree.getRecommendedDegrees(barDuration, barCount);
            if (recommendations.isEmpty()) {
                LOG.warn("No recommended degrees for {} bars at {}, using default: {}", barCount, barDuration,
                        DEFAULT_DEGREE);
                return DEFAULT_DEGREE;
            }
            ElliottDegree selected = recommendations.get(0);
            String seriesName = series.getName() == null || series.getName().isBlank() ? "<unnamed-series>"
                    : series.getName();
            LOG.info("Auto-selected Elliott degree {} for {} {} bars at {}. Candidates: {}", selected, seriesName,
                    barCount, barDuration, recommendations);
            return selected;
        } catch (Exception ex) {
            LOG.warn("Failed to auto-select degree for {} bars at {}, using default: {}", barCount, barDuration,
                    DEFAULT_DEGREE, ex);
            return DEFAULT_DEGREE;
        }
    }
}
