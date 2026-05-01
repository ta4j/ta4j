/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.Arrays;

import org.apache.commons.math3.exception.MathIllegalStateException;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;

final class LpplFitCalibrator {

    private static final double SINGULARITY_THRESHOLD = 1e-9;

    private final LpplCalibrationProfile profile;

    LpplFitCalibrator(LpplCalibrationProfile profile) {
        this.profile = profile;
    }

    LpplFit fit(double[] logPrices) {
        int window = logPrices.length;
        if (window < 5) {
            return LpplFit.invalid(window, LpplExhaustionStatus.INSUFFICIENT_DATA);
        }
        for (double logPrice : logPrices) {
            if (!Double.isFinite(logPrice)) {
                return LpplFit.invalid(window, LpplExhaustionStatus.INVALID_INPUT);
            }
        }

        NonlinearFit seed = gridSearch(logPrices);
        if (seed == null || !seed.isFinite()) {
            return LpplFit.invalid(window, LpplExhaustionStatus.OPTIMIZER_FAILED);
        }

        try {
            LeastSquaresOptimizer.Optimum optimum = optimize(logPrices, seed);
            double[] point = optimum.getPoint().toArray();
            NonlinearFit finalFit = solveLinear(logPrices, point[0], point[1], point[2], optimum.getEvaluations());
            if (finalFit == null || !finalFit.isFinite()) {
                return LpplFit.invalid(window, LpplExhaustionStatus.OPTIMIZER_FAILED);
            }
            return finalFit.toFit(LpplExhaustionStatus.VALID);
        } catch (MathIllegalStateException | IllegalArgumentException e) {
            return LpplFit.invalid(window, LpplExhaustionStatus.OPTIMIZER_FAILED);
        }
    }

    private LeastSquaresOptimizer.Optimum optimize(double[] logPrices, NonlinearFit seed) {
        double[] target = Arrays.copyOf(logPrices, logPrices.length);
        MultivariateJacobianFunction model = point -> valueAndJacobian(logPrices, point.toArray());
        ParameterValidator validator = params -> new ArrayRealVector(clamp(params.toArray(), logPrices.length), false);
        LeastSquaresBuilder builder = new LeastSquaresBuilder().model(model)
                .target(target)
                .start(new double[] { seed.criticalTime, seed.m, seed.omega })
                .maxEvaluations(profile.maxEvaluations())
                .maxIterations(profile.maxEvaluations())
                .parameterValidator(validator)
                .lazyEvaluation(false);
        return new LevenbergMarquardtOptimizer().optimize(builder.build());
    }

    private Pair<RealVector, RealMatrix> valueAndJacobian(double[] logPrices, double[] rawPoint) {
        double[] point = clamp(rawPoint, logPrices.length);
        double[] values = predictedValues(logPrices, point);
        double[][] jacobian = new double[logPrices.length][3];
        for (int parameterIndex = 0; parameterIndex < point.length; parameterIndex++) {
            double delta = finiteDifferenceDelta(parameterIndex, point[parameterIndex]);
            double[] forward = point.clone();
            double[] backward = point.clone();
            forward[parameterIndex] += delta;
            backward[parameterIndex] -= delta;
            forward = clamp(forward, logPrices.length);
            backward = clamp(backward, logPrices.length);

            double[] forwardValues = predictedValues(logPrices, forward);
            double[] backwardValues = predictedValues(logPrices, backward);
            double denominator = forward[parameterIndex] - backward[parameterIndex];
            if (Math.abs(denominator) < SINGULARITY_THRESHOLD) {
                denominator = delta;
            }
            for (int row = 0; row < logPrices.length; row++) {
                jacobian[row][parameterIndex] = (forwardValues[row] - backwardValues[row]) / denominator;
            }
        }
        return new Pair<>(new ArrayRealVector(values, false), new Array2DRowRealMatrix(jacobian, false));
    }

    private double finiteDifferenceDelta(int parameterIndex, double value) {
        if (parameterIndex == 0) {
            return Math.max(1e-3, Math.abs(value) * 1e-5);
        }
        if (parameterIndex == 1) {
            return 1e-5;
        }
        return 1e-4;
    }

    private double[] predictedValues(double[] logPrices, double[] point) {
        NonlinearFit fit = solveLinear(logPrices, point[0], point[1], point[2], 0);
        if (fit == null || !fit.isFinite()) {
            double[] penalty = new double[logPrices.length];
            Arrays.fill(penalty, 1e6);
            return penalty;
        }
        return fit.predicted;
    }

    private double[] clamp(double[] point, int window) {
        window = Math.max(5, window);
        double lastTime = window - 1.0;
        double minTc = lastTime + profile.minCriticalOffset();
        double maxTc = lastTime + profile.maxCriticalOffset();
        return new double[] { clamp(point[0], minTc, maxTc), clamp(point[1], profile.minM(), profile.maxM()),
                clamp(point[2], profile.minOmega(), profile.maxOmega()) };
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return (min + max) * 0.5;
        }
        return Math.max(min, Math.min(max, value));
    }

    private NonlinearFit gridSearch(double[] logPrices) {
        NonlinearFit bestFit = null;
        int lastIndex = logPrices.length - 1;
        double minTc = lastIndex + profile.minCriticalOffset();
        double maxTc = lastIndex + profile.maxCriticalOffset();
        for (double criticalTime = minTc; criticalTime <= maxTc + SINGULARITY_THRESHOLD; criticalTime += profile
                .criticalOffsetStep()) {
            for (int mIndex = 0; mIndex < profile.mSteps(); mIndex++) {
                double m = gridValue(profile.minM(), profile.maxM(), profile.mSteps(), mIndex);
                for (int omegaIndex = 0; omegaIndex < profile.omegaSteps(); omegaIndex++) {
                    double omega = gridValue(profile.minOmega(), profile.maxOmega(), profile.omegaSteps(), omegaIndex);
                    NonlinearFit candidate = solveLinear(logPrices, criticalTime, m, omega, 0);
                    if (candidate != null && candidate.isFinite() && (bestFit == null || candidate.rss < bestFit.rss)) {
                        bestFit = candidate;
                    }
                }
            }
        }
        return bestFit;
    }

    private double gridValue(double min, double max, int steps, int index) {
        if (steps == 1) {
            return (min + max) * 0.5;
        }
        return min + (max - min) * index / (steps - 1.0);
    }

    private NonlinearFit solveLinear(double[] logPrices, double criticalTime, double m, double omega, int evaluations) {
        if (!isValidNonlinearPoint(logPrices.length, criticalTime, m, omega)) {
            return null;
        }
        double[][] x = new double[logPrices.length][4];
        for (int i = 0; i < logPrices.length; i++) {
            double dt = criticalTime - i;
            if (dt <= 0 || !Double.isFinite(dt)) {
                return null;
            }
            double power = Math.pow(dt, m);
            double logDt = Math.log(dt);
            x[i][0] = 1.0;
            x[i][1] = power;
            x[i][2] = power * Math.cos(omega * logDt);
            x[i][3] = power * Math.sin(omega * logDt);
        }
        try {
            RealMatrix design = new Array2DRowRealMatrix(x, false);
            DecompositionSolver solver = new QRDecomposition(design, 1e-10).getSolver();
            RealVector beta = solver.solve(new ArrayRealVector(logPrices, false));
            double[] coefficients = beta.toArray();
            double[] predicted = design.operate(beta).toArray();
            double mean = 0.0;
            for (double logPrice : logPrices) {
                mean += logPrice;
            }
            mean /= logPrices.length;

            double rss = 0.0;
            double tss = 0.0;
            for (int i = 0; i < logPrices.length; i++) {
                double residual = predicted[i] - logPrices[i];
                rss += residual * residual;
                double centered = logPrices[i] - mean;
                tss += centered * centered;
            }
            double rms = Math.sqrt(rss / logPrices.length);
            double rSquared = tss <= SINGULARITY_THRESHOLD ? 0.0 : 1.0 - rss / tss;
            int criticalOffset = (int) Math.round(criticalTime - (logPrices.length - 1.0));
            return new NonlinearFit(logPrices.length, coefficients[0], coefficients[1], coefficients[2],
                    coefficients[3], criticalTime, m, omega, rss, rms, rSquared, criticalOffset, evaluations,
                    predicted);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isValidNonlinearPoint(int window, double criticalTime, double m, double omega) {
        double lastTime = window - 1.0;
        return Double.isFinite(criticalTime) && Double.isFinite(m) && Double.isFinite(omega) && criticalTime > lastTime
                && criticalTime >= lastTime + profile.minCriticalOffset()
                && criticalTime <= lastTime + profile.maxCriticalOffset() && m >= profile.minM() && m <= profile.maxM()
                && omega >= profile.minOmega() && omega <= profile.maxOmega();
    }

    private record NonlinearFit(int window, double a, double b, double c1, double c2, double criticalTime, double m,
            double omega, double rss, double rms, double rSquared, int criticalOffset, int evaluations,
            double[] predicted) {

        boolean isFinite() {
            return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c1) && Double.isFinite(c2)
                    && Double.isFinite(criticalTime) && Double.isFinite(m) && Double.isFinite(omega)
                    && Double.isFinite(rss) && Double.isFinite(rms) && Double.isFinite(rSquared);
        }

        LpplFit toFit(LpplExhaustionStatus status) {
            return new LpplFit(window, status, a, b, c1, c2, criticalTime, m, omega, rss, rms, rSquared, criticalOffset,
                    evaluations);
        }
    }
}
