/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.analysis.frequency;

import java.util.Optional;
import java.util.stream.Stream;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Summary statistics for a numeric sample series with optional annualization
 * metadata.
 *
 * <p>
 * The summary accumulates central moments for mean, variance, skewness, and
 * kurtosis while also tracking the elapsed time between samples (in years). If
 * time deltas are provided, callers can derive an annualization factor for
 * volatility scaling.
 * </p>
 *
 * @since 0.22.2
 */
public final class SampleSummary {

    private final Moments moments;
    private final Num deltaYearsSum;
    private final Num deltaCount;

    private SampleSummary(Moments moments, Num deltaYearsSum, Num deltaCount) {
        this.moments = moments;
        this.deltaYearsSum = deltaYearsSum;
        this.deltaCount = deltaCount;
    }

    /**
     * Builds a summary from frequency-aware samples.
     *
     * @param samples    the samples to summarize
     * @param numFactory the numeric factory to use for calculations
     * @return a summary of the provided samples
     */
    public static SampleSummary fromSamples(Stream<Sample> samples, NumFactory numFactory) {
        var zero = numFactory.zero();
        var acc = samples.reduce(Acc.empty(zero),
                (current, sample) -> current.add(sample.value(), sample.deltaYears(), numFactory),
                (left, right) -> left.merge(right, numFactory));
        return acc.toSummary();
    }

    /**
     * Builds a summary from raw values with no annualization metadata.
     *
     * <p>
     * The resulting summary treats all time deltas as zero, so the
     * {@link #annualizationFactor(NumFactory)} will be empty.
     * </p>
     *
     * @param values     the values to summarize
     * @param numFactory the numeric factory to use for calculations
     * @return a summary of the provided values
     */
    public static SampleSummary fromValues(Stream<Num> values, NumFactory numFactory) {
        return fromSamples(values.map(value -> new Sample(value, numFactory.zero())), numFactory);
    }

    /**
     * Returns the number of samples observed.
     *
     * @return the sample count
     */
    public int count() {
        return moments.count();
    }

    /**
     * Returns the arithmetic mean of the samples.
     *
     * @return the sample mean
     */
    public Num mean() {
        return moments.mean();
    }

    /**
     * Returns the second central moment (sum of squared deviations).
     *
     * @return the second central moment
     */
    public Num m2() {
        return moments.m2();
    }

    /**
     * Returns the third central moment.
     *
     * @return the third central moment
     */
    public Num m3() {
        return moments.m3();
    }

    /**
     * Returns the fourth central moment.
     *
     * @return the fourth central moment
     */
    public Num m4() {
        return moments.m4();
    }

    /**
     * Returns the unbiased sample variance.
     *
     * @param numFactory the numeric factory to use for calculations
     * @return the sample variance (zero when fewer than two samples are available)
     */
    public Num sampleVariance(NumFactory numFactory) {
        return moments.sampleVariance(numFactory);
    }

    /**
     * Returns the sample skewness.
     *
     * @param numFactory the numeric factory to use for calculations
     * @return the sample skewness (zero when fewer than three samples or variance
     *         is zero)
     */
    public Num sampleSkewness(NumFactory numFactory) {
        return moments.sampleSkewness(numFactory);
    }

    /**
     * Returns the sample excess kurtosis.
     *
     * @param numFactory the numeric factory to use for calculations
     * @return the sample kurtosis (zero when fewer than four samples or variance is
     *         zero)
     */
    public Num sampleKurtosis(NumFactory numFactory) {
        return moments.sampleKurtosis(numFactory);
    }

    /**
     * Returns the annualization factor derived from positive time deltas.
     *
     * <p>
     * The factor is {@code sqrt(count / deltaYearsSum)} and is typically used to
     * annualize volatility-like measures. If there are no positive deltas, the
     * result is empty.
     * </p>
     *
     * @param numFactory the numeric factory to use for calculations
     * @return the annualization factor, if time deltas are available
     */
    public Optional<Num> annualizationFactor(NumFactory numFactory) {
        var zero = numFactory.zero();
        if (deltaCount.isLessThanOrEqual(zero) || deltaYearsSum.isLessThanOrEqual(zero)) {
            return Optional.empty();
        }
        return Optional.of(deltaCount.dividedBy(deltaYearsSum).sqrt());
    }

    private record Acc(Moments moments, Num deltaYearsSum, Num deltaCount) {

        static Acc empty(Num zero) {
            return new Acc(Moments.empty(zero), zero, zero);
        }

        Acc add(Num value, Num deltaYears, NumFactory numFactory) {
            var nextMoments = moments.add(value, numFactory);
            if (deltaYears.isLessThanOrEqual(numFactory.zero())) {
                return new Acc(nextMoments, deltaYearsSum, deltaCount);
            }
            return new Acc(nextMoments, deltaYearsSum.plus(deltaYears), deltaCount.plus(numFactory.one()));
        }

        Acc merge(Acc other, NumFactory numFactory) {
            var mergedMoments = moments.merge(other.moments, numFactory);
            return new Acc(mergedMoments, deltaYearsSum.plus(other.deltaYearsSum), deltaCount.plus(other.deltaCount));
        }

        SampleSummary toSummary() {
            return new SampleSummary(moments, deltaYearsSum, deltaCount);
        }
    }

    private record Moments(Num mean, Num m2, Num m3, Num m4, int count) {

        static Moments empty(Num zero) {
            return new Moments(zero, zero, zero, zero, 0);
        }

        Moments add(Num x, NumFactory f) {
            if (count == 0) {
                return new Moments(x, f.zero(), f.zero(), f.zero(), 1);
            }
            var n1 = count;
            var n = count + 1;
            var nNum = f.numOf(n);
            var n1Num = f.numOf(n1);
            var delta = x.minus(mean);
            var deltaN = delta.dividedBy(nNum);
            var deltaN2 = deltaN.multipliedBy(deltaN);
            var term1 = delta.multipliedBy(deltaN).multipliedBy(n1Num);
            var meanNext = mean.plus(deltaN);
            var m4Next = m4.plus(term1.multipliedBy(deltaN2).multipliedBy(f.numOf(n * n - 3 * n + 3)))
                    .plus(deltaN2.multipliedBy(m2).multipliedBy(f.numOf(6)))
                    .minus(deltaN.multipliedBy(m3).multipliedBy(f.numOf(4)));
            var m3Next = m3.plus(term1.multipliedBy(deltaN).multipliedBy(f.numOf(n - 2)))
                    .minus(deltaN.multipliedBy(m2).multipliedBy(f.numOf(3)));
            var m2Next = m2.plus(term1);
            return new Moments(meanNext, m2Next, m3Next, m4Next, n);
        }

        Moments merge(Moments other, NumFactory f) {
            if (other.count == 0) {
                return this;
            }
            if (count == 0) {
                return other;
            }
            var n1 = count;
            var n2 = other.count;
            var n = n1 + n2;
            var n1Num = f.numOf(n1);
            var n2Num = f.numOf(n2);
            var nNum = f.numOf(n);
            var delta = other.mean.minus(mean);
            var delta2 = delta.multipliedBy(delta);
            var delta3 = delta2.multipliedBy(delta);
            var delta4 = delta2.multipliedBy(delta2);
            var meanNext = mean.plus(delta.multipliedBy(n2Num).dividedBy(nNum));
            var m2Next = m2.plus(other.m2).plus(delta2.multipliedBy(n1Num).multipliedBy(n2Num).dividedBy(nNum));
            var m3Next = m3.plus(other.m3)
                    .plus(delta3.multipliedBy(n1Num)
                            .multipliedBy(n2Num)
                            .multipliedBy(f.numOf(n1 - n2))
                            .dividedBy(nNum.multipliedBy(nNum)))
                    .plus(delta.multipliedBy(f.numOf(3))
                            .multipliedBy(n1Num.multipliedBy(other.m2).minus(n2Num.multipliedBy(m2)))
                            .dividedBy(nNum));
            var m4Next = m4.plus(other.m4)
                    .plus(delta4.multipliedBy(n1Num)
                            .multipliedBy(n2Num)
                            .multipliedBy(f.numOf(n1 * n1 - n1 * n2 + n2 * n2))
                            .dividedBy(nNum.multipliedBy(nNum).multipliedBy(nNum)))
                    .plus(delta2.multipliedBy(f.numOf(6))
                            .multipliedBy(n1Num.multipliedBy(n1Num)
                                    .multipliedBy(other.m2)
                                    .plus(n2Num.multipliedBy(n2Num).multipliedBy(m2)))
                            .dividedBy(nNum.multipliedBy(nNum)))
                    .plus(delta.multipliedBy(f.numOf(4))
                            .multipliedBy(n1Num.multipliedBy(other.m3).minus(n2Num.multipliedBy(m3)))
                            .dividedBy(nNum));
            return new Moments(meanNext, m2Next, m3Next, m4Next, n);
        }

        Num sampleVariance(NumFactory f) {
            if (count < 2) {
                return f.zero();
            }
            return m2.dividedBy(f.numOf(count - 1));
        }

        Num sampleSkewness(NumFactory f) {
            if (count < 3 || m2.isZero()) {
                return f.zero();
            }
            var n = f.numOf(count);
            var nMinus1 = f.numOf(count - 1);
            var nMinus2 = f.numOf(count - 2);
            var denom = m2.sqrt().multipliedBy(m2);
            var factor = n.multipliedBy(nMinus1).sqrt().dividedBy(nMinus2);
            return m3.dividedBy(denom).multipliedBy(factor);
        }

        Num sampleKurtosis(NumFactory f) {
            if (count < 4 || m2.isZero()) {
                return f.zero();
            }
            var n = f.numOf(count);
            var nMinus1 = f.numOf(count - 1);
            var nMinus2 = f.numOf(count - 2);
            var nMinus3 = f.numOf(count - 3);
            var m2Squared = m2.multipliedBy(m2);
            var term = n.plus(f.numOf(1)).multipliedBy(m4).dividedBy(m2Squared).minus(nMinus1.multipliedBy(f.numOf(3)));
            return nMinus1.dividedBy(nMinus2.multipliedBy(nMinus3)).multipliedBy(term);
        }
    }
}
