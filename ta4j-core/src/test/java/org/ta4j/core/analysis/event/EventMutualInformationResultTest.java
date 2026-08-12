/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EventMutualInformationResultTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public EventMutualInformationResultTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void emptyRangeResultMustBeUndefined() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // An empty sample range with defined metrics or formed bins is an
        // inconsistent state and must be rejected.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.zero(),
                factory.zero(), factory.zero(), 0, 0, NaN.NaN, 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN,
                0, 0, NaN.NaN, 8, 2, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN,
                0, 0, factory.one(), 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // The undefined empty result is the canonical valid form.
        new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN, 0, 0, NaN.NaN, 8, 0, BinningStrategy.EQUAL_WIDTH, 0,
                3);
    }

    @Test
    public void nonemptyUndefinedResultMustCarryNanMetricsAndNoBins() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // A non-finite predictor sample leaves the counts factual but the
        // metrics undefined: a defined normalized MI or formed bins contradict
        // that state and must be rejected.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(NaN.NaN, factory.one(),
                NaN.NaN, 8, 2, factory.numOf(0.25), 8, 3, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(NaN.NaN, NaN.NaN,
                factory.one(), 8, 2, factory.numOf(0.25), 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // Either raw metric NaN is an undefined state: the other raw metric must
        // be NaN too, even with zero formed bins.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(NaN.NaN, factory.one(),
                NaN.NaN, 8, 2, factory.numOf(0.25), 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(), NaN.NaN,
                NaN.NaN, 8, 2, factory.numOf(0.25), 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // The canonical nonempty undefined result keeps factual counts and rate.
        new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN, 8, 2, factory.numOf(0.25), 8, 0,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void nonemptyResultMustCarryCountConsistentFiniteRate() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // The documented factual prevalence is positiveTargetCount /
        // sampleCount; NaN, out-of-range, or contradictory rates expose
        // misleading diagnostics and must be rejected.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), NaN.NaN, 10, 5, NaN.NaN, 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), NaN.NaN, 10, 5, factory.numOf(0.9), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), NaN.NaN, 10, 5, factory.numOf(1.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // Rates that round to the expected count without being the exact ratio
        // (for example 0.54 for 5 of 10) are still contradictory and must be
        // rejected.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), NaN.NaN, 10, 5, factory.numOf(0.54), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // A rate within a rounded comparison's tolerance (for example 0.500009
        // for 5 of 10) must also be rejected: only the exact Num ratio is
        // consistent with the factual counts.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), NaN.NaN, 10, 5, factory.numOf(0.500009), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // The canonical valid form: a rate consistent with the counts and, for a
        // non-constant target (5 of 10 positives), a defined normalized MI.
        new EventMutualInformationResult(factory.one(), factory.one(), factory.one(), 10, 5, factory.numOf(0.5), 8, 4,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void normalizedMutualInformationStateMustMatchTargetConstancy() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // A constant target (0 or sampleCount positive samples) has zero target
        // entropy, so the evaluator pairs it with NaN normalized MI; a
        // non-constant target carries a defined normalized value. Any other
        // pairing is contradictory: either a defined rate silently dropped or
        // normalized against nothing.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), NaN.NaN, 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), factory.one(), 10, 10, factory.one(), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), factory.one(), 10, 0, factory.zero(), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // A constant target has zero raw mutual information and entropy, so
        // the canonical forms carry zero raw metrics with NaN normalized MI
        // and a factual rate; nonzero raw metrics on a constant target
        // contradict the zero-variance state and must be rejected.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), NaN.NaN, 10, 10, factory.one(), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), NaN.NaN, 10, 0, factory.zero(), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.zero(),
                factory.one(), NaN.NaN, 10, 10, factory.one(), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        new EventMutualInformationResult(factory.zero(), factory.zero(), NaN.NaN, 10, 0, factory.zero(), 8, 4,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
        new EventMutualInformationResult(factory.zero(), factory.zero(), NaN.NaN, 10, 10, factory.one(), 8, 4,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
        // And the non-constant canonical form with a defined normalized value.
        new EventMutualInformationResult(factory.one(), factory.one(), factory.one(), 10, 5, factory.numOf(0.5), 8, 4,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void nonconstantNormalizedMutualInformationMustMatchRawRatio() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();

        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.numOf(0.1), factory.numOf(0.5), factory.numOf(0.9), 10,
                        5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));

        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.numOf(-1), factory.numOf(-2), factory.numOf(0.5), 10, 5,
                        factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        new EventMutualInformationResult(factory.numOf(0.1), factory.numOf(0.5), factory.numOf(0.2), 10, 5,
                factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void definedResultRejectsInfiniteRawMetrics() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // The raw metrics of a defined result are documented as finite mutual
        // information and entropy; an overflowing histogram sum must not pass
        // as a defined result. The DoubleNum infinite probe works under every
        // numeric factory, mirroring how NaN.NaN probes undefined state.
        Num positiveInfinity = DoubleNum.valueOf(Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(positiveInfinity,
                factory.one(), factory.one(), 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                positiveInfinity, factory.one(), 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(positiveInfinity,
                positiveInfinity, factory.one(), 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // The normalized value of a non-constant target must be finite and in
        // [0, 1]: only rounding-scale deviations from the bounds are possible.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), positiveInfinity, 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), factory.numOf(1.5), 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.one(), factory.one(), factory.numOf(-0.5), 10, 5,
                        factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // Rounding-scale deviations from the bounds stay accepted.
        new EventMutualInformationResult(factory.one(), factory.one(), factory.numOf(1.0 + 1.0e-13), 10, 5,
                factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3);
    }
}
