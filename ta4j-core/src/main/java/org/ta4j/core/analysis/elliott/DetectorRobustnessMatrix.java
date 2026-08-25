/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;

/**
 * Runs topology-only recognition over the preregistered detector matrix.
 *
 * @since 0.24.2
 */
final class DetectorRobustnessMatrix {

    private DetectorRobustnessMatrix() {
    }

    /**
     * Returns the inexpensive detector configurations frozen for the study.
     *
     * @return detector configurations in deterministic order
     */
    static List<DetectorSpec> defaults() {
        // Must stay aligned with the frozen CF-525 protocol's registered
        // detector matrix (fractal-w3, fractal-w5, prominence-default,
        // slope-change-w5).
        return List.of(new DetectorSpec("fractal-w3", () -> SwingDetectors.fractal(3)),
                new DetectorSpec("fractal-w5", () -> SwingDetectors.fractal(5)),
                new DetectorSpec("prominence-default", SwingDetectors::prominence),
                new DetectorSpec("slope-change-w5", () -> SwingDetectors.slopeChange(5)));
    }

    /**
     * Evaluates one topology-only MOTIVE_5 mode per detector configuration.
     *
     * @param series     source series
     * @param fromIndex  first evaluated bar (inclusive)
     * @param toIndex    last evaluated bar (inclusive)
     * @param partitions locked study partitions
     * @param detectors  detector configurations
     * @return deterministic detector matrix report
     */
    static StudyReport.RobustnessReport evaluate(final BarSeries series, final int fromIndex, final int toIndex,
            final StudyRunner.Partitions partitions, final List<DetectorSpec> detectors) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(partitions, "partitions");
        Objects.requireNonNull(detectors, "detectors");
        final List<StudyReport.DetectorResult> results = new ArrayList<>(detectors.size());
        for (final DetectorSpec detector : detectors) {
            final StudyReport.ModeReport mode = StudyRunner.evaluateTopologyMode(series, fromIndex, toIndex, partitions,
                    detector.factory(), TopologyGrammar.MOTIVE_5, "topology-only");
            results.add(new StudyReport.DetectorResult(detector.name(), mode));
        }
        return new StudyReport.RobustnessReport(results);
    }

    /**
     * Named immutable detector supplier for the matrix.
     *
     * @since 0.24.2
     */
    record DetectorSpec(String name, Supplier<SwingDetector> factory) {
        DetectorSpec {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("detector name must not be blank");
            }
            factory = Objects.requireNonNull(factory, "factory");
        }
    }
}
