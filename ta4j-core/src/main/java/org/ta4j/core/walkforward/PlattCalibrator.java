/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;

/**
 * Logistic (Platt) probability calibrator using gradient descent.
 *
 * @since 0.22.4
 */
public final class PlattCalibrator implements ProbabilityCalibrator {

    private static final double EPSILON = 1.0e-12;

    private double a;
    private double b;

    /**
     * Creates a Platt calibrator with identity initialization.
     *
     * @since 0.22.4
     */
    public PlattCalibrator() {
        this.a = 1.0;
        this.b = 0.0;
    }

    @Override
    public void fit(List<Double> predictedProbabilities, List<Double> observedProbabilities) {
        if (predictedProbabilities == null || observedProbabilities == null
                || predictedProbabilities.size() != observedProbabilities.size() || predictedProbabilities.isEmpty()) {
            return;
        }

        double learningRate = 0.05;
        int iterations = 200;

        for (int iteration = 0; iteration < iterations; iteration++) {
            double gradientA = 0.0;
            double gradientB = 0.0;

            for (int i = 0; i < predictedProbabilities.size(); i++) {
                double raw = clamp(predictedProbabilities.get(i));
                double observed = clamp(observedProbabilities.get(i));
                double logit = Math.log(raw / Math.max(EPSILON, 1.0 - raw));
                double probability = sigmoid((a * logit) + b);
                double error = probability - observed;
                gradientA += error * logit;
                gradientB += error;
            }

            gradientA /= predictedProbabilities.size();
            gradientB /= predictedProbabilities.size();
            a -= learningRate * gradientA;
            b -= learningRate * gradientB;
        }
    }

    @Override
    public double calibrate(double rawProbability) {
        double raw = clamp(rawProbability);
        double logit = Math.log(raw / Math.max(EPSILON, 1.0 - raw));
        return clamp(sigmoid((a * logit) + b));
    }

    private static double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.5;
        }
        return Math.max(EPSILON, Math.min(1.0 - EPSILON, value));
    }
}
