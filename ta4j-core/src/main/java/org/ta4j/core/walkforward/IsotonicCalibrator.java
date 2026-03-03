/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Isotonic probability calibrator using the pool-adjacent-violators algorithm.
 *
 * @since 0.22.4
 */
public final class IsotonicCalibrator implements ProbabilityCalibrator {

    private List<Double> thresholds = List.of(0.0, 1.0);
    private List<Double> values = List.of(0.0, 1.0);

    @Override
    public void fit(List<Double> predictedProbabilities, List<Double> observedProbabilities) {
        if (predictedProbabilities == null || observedProbabilities == null
                || predictedProbabilities.size() != observedProbabilities.size() || predictedProbabilities.isEmpty()) {
            return;
        }

        List<CalibrationPoint> points = new ArrayList<>(predictedProbabilities.size());
        for (int i = 0; i < predictedProbabilities.size(); i++) {
            points.add(new CalibrationPoint(clamp(predictedProbabilities.get(i)), clamp(observedProbabilities.get(i))));
        }
        points.sort(Comparator.comparingDouble(CalibrationPoint::predicted));

        List<Block> blocks = new ArrayList<>();
        for (CalibrationPoint point : points) {
            blocks.add(new Block(point.predicted(), point.predicted(), point.observed(), 1));
            while (blocks.size() >= 2) {
                Block right = blocks.get(blocks.size() - 1);
                Block left = blocks.get(blocks.size() - 2);
                if (left.mean() <= right.mean()) {
                    break;
                }
                Block merged = left.merge(right);
                blocks.remove(blocks.size() - 1);
                blocks.remove(blocks.size() - 1);
                blocks.add(merged);
            }
        }

        List<Double> newThresholds = new ArrayList<>();
        List<Double> newValues = new ArrayList<>();
        for (Block block : blocks) {
            newThresholds.add(block.maxPredicted());
            newValues.add(clamp(block.mean()));
        }

        if (!newThresholds.isEmpty()) {
            this.thresholds = List.copyOf(newThresholds);
            this.values = List.copyOf(newValues);
        }
    }

    @Override
    public double calibrate(double rawProbability) {
        double p = clamp(rawProbability);
        for (int i = 0; i < thresholds.size(); i++) {
            if (p <= thresholds.get(i)) {
                return values.get(i);
            }
        }
        return values.get(values.size() - 1);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.5;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private record CalibrationPoint(double predicted, double observed) {
    }

    private record Block(double minPredicted, double maxPredicted, double observedSum, int weight) {

        double mean() {
            return observedSum / weight;
        }

        Block merge(Block other) {
            return new Block(Math.min(minPredicted, other.minPredicted), Math.max(maxPredicted, other.maxPredicted),
                    observedSum + other.observedSum, weight + other.weight);
        }
    }
}
