/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward.calibration;

import java.util.List;

/**
 * Calibrates raw probabilities against observed outcomes.
 *
 * @since 0.22.4
 */
public interface ProbabilityCalibrator {

    /**
     * Fits calibrator parameters.
     *
     * @param predictedProbabilities raw predicted probabilities in {@code [0,1]}
     * @param observedProbabilities  observed probabilities in {@code [0,1]}
     * @since 0.22.4
     */
    void fit(List<Double> predictedProbabilities, List<Double> observedProbabilities);

    /**
     * Applies calibration to a raw probability.
     *
     * @param rawProbability raw probability in {@code [0,1]}
     * @return calibrated probability in {@code [0,1]}
     * @since 0.22.4
     */
    double calibrate(double rawProbability);
}
