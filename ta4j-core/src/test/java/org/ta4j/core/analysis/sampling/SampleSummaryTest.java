/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.sampling;

import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.ta4j.core.analysis.frequency.Sample;
import org.ta4j.core.analysis.frequency.SampleSummary;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.num.NumFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SampleSummaryTest extends AbstractCriterionTest {

    public SampleSummaryTest(NumFactory numFactory) {
        super(params -> null, numFactory);
    }

    @Test
    public void computesMomentsForDeterministicSeries() {
        var returns = new double[] { 0.05d, -0.02d, 0.04d, 0.01d, 0.03d };
        var deltas = new double[] { 0.5d, 0.25d, 0.25d, 0.5d, 1.0d };

        var summary = SampleSummary.fromSamples(
                IntStream.range(0, returns.length).mapToObj(i -> new Sample(numOf(returns[i]), numOf(deltas[i]))),
                numFactory);

        var expectedMean = DoubleStream.of(returns).average().orElseThrow();
        var expectedM2 = 0d;
        var expectedM3 = 0d;
        var expectedM4 = 0d;
        for (double value : returns) {
            var diff = value - expectedMean;
            var diff2 = diff * diff;
            expectedM2 += diff2;
            expectedM3 += diff2 * diff;
            expectedM4 += diff2 * diff2;
        }
        var expectedVariance = expectedM2 / (returns.length - 1);
        var expectedSkewness = Math.sqrt(returns.length * (returns.length - 1d)) / (returns.length - 2d)
                * (expectedM3 / Math.pow(expectedM2, 1.5d));
        var expectedKurtosis = (returns.length - 1d) / ((returns.length - 2d) * (returns.length - 3d))
                * (((returns.length + 1d) * expectedM4 / (expectedM2 * expectedM2)) - (3d * (returns.length - 1d)));
        var expectedAnnualization = Math.sqrt(deltas.length / DoubleStream.of(deltas).sum());

        assertEquals(returns.length, summary.count());
        assertEquals(expectedMean, summary.mean().doubleValue(), 1e-12);
        assertEquals(expectedM2, summary.m2().doubleValue(), 1e-12);
        assertEquals(expectedM3, summary.m3().doubleValue(), 1e-12);
        assertEquals(expectedM4, summary.m4().doubleValue(), 1e-12);
        assertEquals(expectedVariance, summary.sampleVariance(numFactory).doubleValue(), 1e-12);
        assertEquals(expectedSkewness, summary.sampleSkewness(numFactory).doubleValue(), 1e-12);
        assertEquals(expectedKurtosis, summary.sampleKurtosis(numFactory).doubleValue(), 1e-12);
        assertEquals(expectedAnnualization, summary.annualizationFactor(numFactory).orElseThrow().doubleValue(), 1e-12);
    }

    @Test
    public void fromValuesTreatsAnnualizationAsEmpty() {
        var values = new double[] { 0.1d, 0.2d, 0.3d };

        var summary = SampleSummary.fromValues(DoubleStream.of(values).mapToObj(this::numOf), numFactory);

        var expectedMean = DoubleStream.of(values).average().orElseThrow();

        assertEquals(values.length, summary.count());
        assertEquals(expectedMean, summary.mean().doubleValue(), 1e-12);
        assertTrue(summary.annualizationFactor(numFactory).isEmpty());
    }

    @Test
    public void skewnessAndKurtosisRemainZeroWithInsufficientCount() {
        var summary = SampleSummary.fromSamples(IntStream.range(0, 2).mapToObj(i -> new Sample(numOf(i), numOf(0.5d))),
                numFactory);

        assertEquals(2, summary.count());
        assertEquals(0d, summary.sampleSkewness(numFactory).doubleValue(), 0d);
        assertEquals(0d, summary.sampleKurtosis(numFactory).doubleValue(), 0d);
    }
}
