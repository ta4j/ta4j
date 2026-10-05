/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.statistics.SinglePrecisionNumFactory;
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
        // The canonical valid form: a rate consistent with the counts, the
        // binary entropy ln 2 of the 5-of-10 positive rate, and, for a
        // non-constant target, a defined normalized MI equal to MI / H(Y).
        new EventMutualInformationResult(factory.numOf(0.5), factory.numOf(Math.log(2)),
                factory.numOf(0.5 / Math.log(2)), 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3);
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
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.one(), factory.numOf(Math.log(2)), NaN.NaN, 10, 5,
                        factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
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
        new EventMutualInformationResult(factory.numOf(0.5), factory.numOf(Math.log(2)),
                factory.numOf(0.5 / Math.log(2)), 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void nonconstantNormalizedMutualInformationMustMatchRawRatio() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();

        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.numOf(0.1), factory.numOf(Math.log(2)),
                        factory.numOf(0.9), 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));

        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.numOf(-1), factory.numOf(-2), factory.numOf(0.5), 10, 5,
                        factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        new EventMutualInformationResult(factory.numOf(0.1), factory.numOf(Math.log(2)),
                factory.numOf(0.1 / Math.log(2)), 10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3);
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
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.one(), factory.numOf(Math.log(2)), positiveInfinity, 10,
                        5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.one(), factory.numOf(Math.log(2)), factory.numOf(1.5),
                        10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.one(), factory.numOf(Math.log(2)), factory.numOf(-0.5),
                        10, 5, factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // Rounding-scale deviations from the bounds stay accepted.
        new EventMutualInformationResult(factory.numOf(0.5), factory.numOf(Math.log(2)),
                factory.numOf(0.5 / Math.log(2) + 1.0e-13), 10, 5, factory.numOf(0.5), 8, 4,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void definedResultMustCarryAtLeastOneEffectiveBin() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // The evaluator always forms at least one bin from a nonempty finite
        // sample range, so defined metrics alongside zero effective bins is a
        // contradictory state: both the constant-target form (zero raw
        // metrics, NaN normalized MI) and the non-constant form must be
        // rejected.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.zero(),
                factory.zero(), NaN.NaN, 10, 0, factory.zero(), 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.one(),
                factory.one(), factory.one(), 10, 5, factory.numOf(0.5), 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
    }

    @Test
    public void targetEntropyMustMatchTheBinaryEntropyOfTheCounts() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // Review regression: the documented binary entropy of a 1-of-2
        // positive rate is ln 2; an entropy of 0.1 nats contradicts the
        // factual counts and must be rejected instead of silently distorting
        // the normalized MI.
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.numOf(0.1), factory.numOf(0.1), factory.numOf(0.2), 2, 1,
                        factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.numOf(0.1), factory.numOf(0.7), factory.numOf(0.2), 2, 1,
                        factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // The entropy is recomputed with this factory from the same counts,
        // so the exact binary entropy and rounding-scale deviations from it
        // stay accepted.
        Num entropy = factory.numOf(Math.log(2));
        new EventMutualInformationResult(factory.numOf(0.2).multipliedBy(entropy), entropy, factory.numOf(0.2), 2, 1,
                factory.numOf(0.5), 8, 4, BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void singleEffectiveBinMustCarryZeroMutualInformation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // Review regression: a single effective bin means every predictor
        // sample falls in the same bin, so the predictor is constant and the
        // raw mutual information must be exactly zero. Positive MI alongside
        // one formed bin is an impossible score and must be rejected, for
        // constant and non-constant targets alike.
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.numOf(0.1), factory.numOf(Math.log(2)),
                        factory.numOf(0.2), 2, 1, factory.numOf(0.5), 8, 1, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // Zero MI alongside the single bin is the canonical form for both
        // the non-constant and the constant target.
        new EventMutualInformationResult(factory.zero(), factory.numOf(Math.log(2)), factory.zero(), 2, 1,
                factory.numOf(0.5), 8, 1, BinningStrategy.EQUAL_WIDTH, 0, 3);
        new EventMutualInformationResult(factory.zero(), factory.zero(), NaN.NaN, 2, 0, factory.zero(), 8, 1,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
        new EventMutualInformationResult(factory.zero(), factory.zero(), NaN.NaN, 2, 2, factory.one(), 8, 1,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void effectiveBinCountCannotExceedTheSampleCount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // Review regression: equal-frequency binning creates at most one bin
        // per nonempty sample group, so a direct construction or deserialized
        // value with more effective bins than samples (for example two samples
        // with ten requested and ten effective bins) reports an impossible
        // diagnostic and must be rejected.
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.numOf(0.5), factory.numOf(Math.log(2)),
                        factory.numOf(0.5 / Math.log(2)), 2, 1, factory.numOf(0.5), 10, 10,
                        BinningStrategy.EQUAL_FREQUENCY, 0, 3));
        // The same bound holds for undefined results: NaN metrics with more
        // formed bins than samples stay inconsistent.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN,
                2, 1, factory.numOf(0.5), 10, 3, BinningStrategy.EQUAL_FREQUENCY, 0, 3));
        // Equal-width binning divides the value range instead, so requested
        // bins beyond the sample count remain representable and the
        // evaluator's reported diagnostic is accepted as-is.
        new EventMutualInformationResult(factory.numOf(0.5), factory.numOf(Math.log(2)),
                factory.numOf(0.5 / Math.log(2)), 2, 1, factory.numOf(0.5), 10, 10, BinningStrategy.EQUAL_WIDTH, 0, 3);
        // A defined equal-frequency result never needs more effective bins
        // than samples: the canonical two-sample form with a single effective
        // bin stays valid.
        new EventMutualInformationResult(factory.zero(), factory.numOf(Math.log(2)), factory.zero(), 2, 1,
                factory.numOf(0.5), 10, 1, BinningStrategy.EQUAL_FREQUENCY, 0, 3);
    }

    @Test
    public void requestedBinCountCannotExceedTheEvaluatorCeiling() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // Review regression: no evaluator configuration can request more than
        // MAX_PREDICTOR_BIN_COUNT bins, so a direct construction or
        // deserialized value above the ceiling (for example 1,000,001)
        // describes a diagnostic the evaluator rejects and must be refused
        // here too, for defined and undefined results alike.
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(factory.zero(), factory.numOf(Math.log(2)), factory.zero(), 2, 1,
                        factory.numOf(0.5), EventMutualInformationConfig.MAX_PREDICTOR_BIN_COUNT + 1, 1,
                        BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN, 2, 1, factory.numOf(0.5),
                        EventMutualInformationConfig.MAX_PREDICTOR_BIN_COUNT + 1, 0, BinningStrategy.EQUAL_WIDTH, 0,
                        3));
        // The ceiling value itself stays accepted.
        new EventMutualInformationResult(factory.zero(), factory.numOf(Math.log(2)), factory.zero(), 2, 1,
                factory.numOf(0.5), EventMutualInformationConfig.MAX_PREDICTOR_BIN_COUNT, 1,
                BinningStrategy.EQUAL_WIDTH, 0, 3);
    }

    @Test
    public void normalizedBoundStaysAtMetricPrecisionRegardlessOfBinCount() {
        // Review regression: the normalized bound must not scale with the bin
        // count, or a sparse equal-width table (two endpoint samples in a
        // 1,000,000-bin request) inflates the tolerance beyond any meaningful
        // range and a directly constructed result can claim MI = 1.05 * H(Y),
        // although mutual information can never exceed the target entropy.
        // Only the evaluator's accumulation-scale roundoff may be adjusted;
        // the public result bound stays at the factory epsilon.
        NumFactory floatFactory = SinglePrecisionNumFactory.getInstance();
        Num half = floatFactory.numOf(0.5);
        Num entropy = half.multipliedBy(half.log()).plus(half.multipliedBy(half.log())).negate();
        Num mutualInformation = entropy.multipliedBy(floatFactory.numOf(1.05));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationResult(mutualInformation, entropy, floatFactory.numOf(1.05), 2, 1, half,
                        EventMutualInformationConfig.MAX_PREDICTOR_BIN_COUNT,
                        EventMutualInformationConfig.MAX_PREDICTOR_BIN_COUNT, BinningStrategy.EQUAL_WIDTH, 0, 0));
    }
}
