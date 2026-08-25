/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Composition decorator that fattens the tails of an inner technique's centered
 * samples with a mean-normalized Student-t scale mixing factor.
 *
 * <p>
 * Each sample is rescaled around the drift path by an independent factor
 * {@code f = sqrt(df / chiSq(df)) / E[sqrt(df / chiSq(df))]} drawn from the
 * Student-t scale distribution with {@code degreesOfFreedom}:
 *
 * <pre>
 * path = h * drift + f * (sample - h * drift)
 * </pre>
 *
 * <p>
 * The factor has expectation 1 (no central-scale shift) but a heavy right tail,
 * so the quantile spread grows where it matters for coverage in high-volatility
 * regimes, while the mean and the calm-regime center are preserved. Higher
 * degrees of freedom approximate the unchanged gaussian case; lower values
 * permit more extreme scale draws. The numerator {@code sqrt(M / chiSq(M))}
 * uses {@code degreesOfFreedom} independent standard-normal draws from
 * {@link MonteCarloContext#random()}.
 *
 * <p>
 * This decorator stresses the seam contract: it draws exclusively from
 * {@link MonteCarloContext#random()}, returns exactly
 * {@code context.iterationCount()} finite samples, propagates a {@code null}
 * (unstable) result from the inner method, and declares the forecast unstable
 * when the inner method returns the wrong sample count or the drift is not
 * finite.
 *
 * @see MonteCarloMethod
 * @since 0.24.2
 */
public final class StudentTScaleMixingMonteCarloMethod implements MonteCarloMethod {

    /** Default degrees of freedom of the mixing scale. */
    public static final int DEFAULT_DEGREES_OF_FREEDOM = 5;

    private final MonteCarloMethod inner;
    private final int degreesOfFreedom;
    private final double scaleMean;

    /**
     * Wraps an inner technique with the default degrees of freedom
     * ({@value #DEFAULT_DEGREES_OF_FREEDOM}).
     *
     * @param inner technique whose centered samples are tail-mixed
     * @since 0.24.2
     */
    public StudentTScaleMixingMonteCarloMethod(MonteCarloMethod inner) {
        this(inner, DEFAULT_DEGREES_OF_FREEDOM);
    }

    /**
     * Wraps an inner technique with a configurable degrees of freedom.
     *
     * @param inner            technique whose centered samples are rescaled
     * @param degreesOfFreedom of the mixing Student-t scale, must be >= 2
     * @since 0.24.2
     */
    public StudentTScaleMixingMonteCarloMethod(MonteCarloMethod inner, int degreesOfFreedom) {
        if (inner == null) {
            throw new IllegalArgumentException("inner must not be null");
        }
        if (degreesOfFreedom < 2) {
            throw new IllegalArgumentException("degreesOfFreedom must be >= 2");
        }
        this.inner = inner;
        this.degreesOfFreedom = degreesOfFreedom;
        this.scaleMean = tScaleMean(degreesOfFreedom);
    }

    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        List<Num> samples = inner.terminalReturns(context);
        if (samples == null || samples.size() != context.iterationCount()) {
            return null;
        }
        ReturnMoments moments = context.moments();
        if (moments == null || !moments.isStable()) {
            return null;
        }
        NumFactory numFactory = context.numFactory();
        Num drift = moments.drift();
        if (!Num.isFinite(drift)) {
            return null;
        }
        Num driftPath = drift.multipliedBy(numFactory.numOf(context.horizon()));
        RandomGenerator random = context.random();
        List<Num> mixed = new ArrayList<>(context.iterationCount());
        for (Num sample : samples) {
            double factor = tScaleDraw(random) / scaleMean;
            Num centered = sample.minus(driftPath);
            Num scaled = driftPath.plus(centered.multipliedBy(numFactory.numOf(factor)));
            if (!Num.isFinite(scaled)) {
                return null;
            }
            mixed.add(scaled);
        }
        return mixed;
    }

    /** {@code sqrt(df / chiSq(df))} draw from the Student-t scale distribution. */
    private double tScaleDraw(RandomGenerator random) {
        double chiSq = 0d;
        for (int i = 0; i < degreesOfFreedom; i++) {
            double normal = random.nextGaussian();
            chiSq += normal * normal;
        }
        return Math.sqrt(degreesOfFreedom / chiSq);
    }

    /**
     * Closed-form expectation of {@code sqrt(df / chiSq(df))},
     * {@code sqrt(df/2) * Gamma((df-1)/2) / Gamma(df/2)}, needed so the mixing
     * factor has mean 1. Uses a Lanczos approximation of the gamma function,
     * sufficient for the small half-integer arguments used here.
     */
    private static double tScaleMean(int degreesOfFreedom) {
        return Math.sqrt(degreesOfFreedom / 2d) * gamma((degreesOfFreedom - 1d) / 2d) / gamma(degreesOfFreedom / 2d);
    }

    /** Lanczos approximation of {@code Gamma(x)} for {@code x > 0}. */
    private static double gamma(double x) {
        double[] coefficients = { 0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
                1.5056327351493116e-7 };
        if (x < 0.5) {
            return Math.PI / (Math.sin(Math.PI * x) * gamma(1d - x));
        }
        x -= 1d;
        double a = coefficients[0];
        double t = x + 7.5;
        for (int i = 1; i < coefficients.length; i++) {
            a += coefficients[i] / (x + i);
        }
        return Math.sqrt(2d * Math.PI) * Math.pow(t, x + 0.5) * Math.exp(-t) * a;
    }

    @Override
    public String toString() {
        return "StudentTScaleMixingMonteCarloMethod[degreesOfFreedom=" + degreesOfFreedom + ", inner=" + inner + "]";
    }
}