/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Student-t scale mixing behind
 * {@link MonteCarloMethod#withStudentTScaleMixing}.
 *
 * <p>
 * Each inner sample is rescaled around the drift path by an independent
 * mean-one factor {@code f = sqrt(df / chiSq(df)) / E[sqrt(df / chiSq(df))]}:
 *
 * <pre>
 * path = h * drift + f * (sample - h * drift)
 * </pre>
 *
 * The chi-square denominator comes from
 * {@link RandomSamplers#nextChiSquared(RandomGenerator, int)}, whose expected
 * cost stays bounded for every {@code degreesOfFreedom}. The forecast is
 * unstable when the inner technique breaks the seam contract or the moments are
 * unstable or carry a drift the context factory cannot represent.
 */
final class StudentTScaleMixingMonteCarloMethod implements MonteCarloMethod {

    /** Default degrees of freedom of the mixing scale. */
    static final int DEFAULT_DEGREES_OF_FREEDOM = 5;

    private final MonteCarloMethod inner;
    private final int degreesOfFreedom;
    private final double scaleMean;

    StudentTScaleMixingMonteCarloMethod(MonteCarloMethod inner) {
        this(inner, DEFAULT_DEGREES_OF_FREEDOM);
    }

    StudentTScaleMixingMonteCarloMethod(MonteCarloMethod inner, int degreesOfFreedom) {
        if (degreesOfFreedom < 2) {
            throw new IllegalArgumentException("degreesOfFreedom must be >= 2");
        }
        this.inner = Objects.requireNonNull(inner, "inner");
        this.degreesOfFreedom = degreesOfFreedom;
        this.scaleMean = tScaleMean(degreesOfFreedom);
    }

    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        List<Num> samples = MonteCarloArithmetic.normalizeSamples(inner.terminalReturns(context), context);
        if (samples == null) {
            return null;
        }
        ReturnMoments moments = context.moments();
        if (moments == null || !moments.isStable()) {
            return null;
        }
        NumFactory numFactory = context.numFactory();
        Num drift = MonteCarloArithmetic.normalize(moments.drift(), numFactory);
        if (drift == null) {
            return null;
        }
        Num driftPath = drift.multipliedBy(numFactory.numOf(context.horizon()));
        // The drift path itself need not fit when its affine contribution does.
        // Keep that center wide until after weighting and addition.
        BigDecimal extendedDriftPath = Num.isFinite(driftPath) ? null
                : drift.bigDecimalValue().multiply(BigDecimal.valueOf(context.horizon()));
        RandomGenerator random = context.random();
        List<Num> mixed = new ArrayList<>(samples.size());
        for (Num sample : samples) {
            Num scale = numFactory.numOf(tScaleDraw(random) / scaleMean);
            if (!Num.isFinite(scale)) {
                return null;
            }
            Num scaled;
            if (extendedDriftPath == null) {
                scaled = MonteCarloArithmetic.affine(driftPath, sample, scale, numFactory);
            } else {
                BigDecimal weight = scale.bigDecimalValue();
                scaled = numFactory.numOf(extendedDriftPath.multiply(BigDecimal.ONE.subtract(weight))
                        .add(sample.bigDecimalValue().multiply(weight)));
            }
            if (!Num.isFinite(scaled)) {
                return null;
            }
            mixed.add(scaled);
        }
        return mixed;
    }

    /** {@code sqrt(df / chiSq(df))} draw from the Student-t scale distribution. */
    private double tScaleDraw(RandomGenerator random) {
        return Math.sqrt(degreesOfFreedom / RandomSamplers.nextChiSquared(random, degreesOfFreedom));
    }

    /**
     * Closed-form expectation of {@code sqrt(df / chiSq(df))},
     * {@code sqrt(df/2) * Gamma((df-1)/2) / Gamma(df/2)}, needed so the mixing
     * factor has mean 1. Computed in log space so large degrees of freedom (where
     * the individual Gamma terms overflow) still yield a finite ratio that
     * approaches 1 as df grows.
     *
     * @param degreesOfFreedom the mixing degrees of freedom, &gt;= 2
     * @return the finite scale expectation
     */
    private static double tScaleMean(int degreesOfFreedom) {
        return Math.sqrt(degreesOfFreedom / 2d)
                * Math.exp(logGamma((degreesOfFreedom - 1d) / 2d) - logGamma(degreesOfFreedom / 2d));
    }

    /**
     * Natural logarithm of the gamma function via the Lanczos approximation,
     * computed in log space so large arguments never overflow intermediate terms.
     *
     * @param x a positive argument
     * @return {@code ln Gamma(x)}
     */
    private static double logGamma(double x) {
        final double[] coefficients = { 0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
                1.5056327351493116e-7 };
        if (x < 0.5) {
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * x)) - logGamma(1d - x);
        }
        x -= 1d;
        double a = coefficients[0];
        double t = x + 7.5;
        for (int i = 1; i < coefficients.length; i++) {
            a += coefficients[i] / (x + i);
        }
        return 0.5 * Math.log(2d * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }

    @Override
    public String toString() {
        return "StudentTScaleMixingMonteCarloMethod[degreesOfFreedom=" + degreesOfFreedom + ", inner=" + inner + "]";
    }
}
