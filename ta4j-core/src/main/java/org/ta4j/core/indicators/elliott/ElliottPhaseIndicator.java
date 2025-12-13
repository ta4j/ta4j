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
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Tracks the current Elliott wave phase by reusing cached swing history.
 *
 * <p>
 * The indicator produces {@link ElliottPhase} values representing the latest
 * impulsive or corrective wave. Helper accessors expose the impulse and
 * corrective segments so that trading rules can assert higher level structure
 * before acting.
 *
 * @since 0.22.0
 */
public class ElliottPhaseIndicator extends RecursiveCachedIndicator<ElliottPhase> {

    private final ElliottSwingIndicator swingIndicator;
    private final ElliottFibonacciValidator fibonacciValidator;
    private final NumFactory numFactory;

    /**
     * @param swingIndicator swing source used for detecting Elliott phases
     * @since 0.22.0
     */
    public ElliottPhaseIndicator(final ElliottSwingIndicator swingIndicator) {
        this(swingIndicator, new ElliottFibonacciValidator(requireFactory(swingIndicator)));
    }

    /**
     * @param swingIndicator     swing source used for detecting Elliott phases
     * @param fibonacciValidator validator responsible for Fibonacci conformance
     * @since 0.22.0
     */
    public ElliottPhaseIndicator(final ElliottSwingIndicator swingIndicator,
            final ElliottFibonacciValidator fibonacciValidator) {
        super(Objects.requireNonNull(swingIndicator, "swingIndicator"));
        this.swingIndicator = swingIndicator;
        this.fibonacciValidator = Objects.requireNonNull(fibonacciValidator, "fibonacciValidator");
        this.numFactory = requireFactory(swingIndicator);
    }

    private static NumFactory requireFactory(final ElliottSwingIndicator swingIndicator) {
        final NumFactory factory = Objects.requireNonNull(swingIndicator, "swingIndicator").getBarSeries().numFactory();
        if (factory == null) {
            throw new IllegalArgumentException("Swing indicator must expose a backing series factory");
        }
        return factory;
    }

    @Override
    protected ElliottPhase calculate(final int index) {
        final ElliottSwingMetadata metadata = metadata(index);
        if (!metadata.isValid() || metadata.isEmpty()) {
            return ElliottPhase.NONE;
        }

        final ImpulseAssessment impulse = assessImpulse(metadata);
        if (!impulse.phase.isImpulse()) {
            return ElliottPhase.NONE;
        }

        if (impulse.phase != ElliottPhase.WAVE5) {
            return impulse.phase;
        }

        if (metadata.size() <= impulse.startIndex + impulse.segment.size()) {
            return ElliottPhase.WAVE5;
        }

        final ElliottPhase correctivePhase = evaluateCorrective(metadata,
                metadata.subList(impulse.startIndex + impulse.segment.size(), metadata.size()), impulse.rising);
        if (correctivePhase.isCorrective()) {
            return correctivePhase;
        }
        return ElliottPhase.WAVE5;
    }

    @Override
    public int getCountOfUnstableBars() {
        return swingIndicator.getCountOfUnstableBars();
    }

    /**
     * @param index bar index
     * @return immutable impulse swings considered for the phase at {@code index}
     * @since 0.22.0
     */
    public List<ElliottSwing> impulseSwings(final int index) {
        final ElliottSwingMetadata metadata = metadata(index);
        if (!metadata.isValid()) {
            return List.of();
        }
        return List.copyOf(assessImpulse(metadata).segment);
    }

    /**
     * @param index bar index
     * @return immutable corrective swings considered for the phase at {@code index}
     * @since 0.22.0
     */
    public List<ElliottSwing> correctiveSwings(final int index) {
        final ElliottSwingMetadata metadata = metadata(index);
        if (!metadata.isValid()) {
            return List.of();
        }
        final ImpulseAssessment impulse = assessImpulse(metadata);
        if (impulse.phase != ElliottPhase.WAVE5) {
            return List.of();
        }
        final List<ElliottSwing> corrective = metadata.subList(impulse.startIndex + impulse.segment.size(),
                metadata.size());
        return List.copyOf(corrective);
    }

    /**
     * @param index bar index
     * @return {@code true} once five waves are confirmed
     * @since 0.22.0
     */
    public boolean isImpulseConfirmed(final int index) {
        final ElliottSwingMetadata metadata = metadata(index);
        if (!metadata.isValid()) {
            return false;
        }
        return assessImpulse(metadata).phase == ElliottPhase.WAVE5;
    }

    /**
     * @param index bar index
     * @return {@code true} once the corrective sequence satisfies A-B-C
     *         requirements
     * @since 0.22.0
     */
    public boolean isCorrectiveConfirmed(final int index) {
        final ElliottSwingMetadata metadata = metadata(index);
        if (!metadata.isValid()) {
            return false;
        }
        final ImpulseAssessment impulse = assessImpulse(metadata);
        if (impulse.phase != ElliottPhase.WAVE5) {
            return false;
        }
        final ElliottPhase corrective = evaluateCorrective(metadata,
                metadata.subList(impulse.startIndex + impulse.segment.size(), metadata.size()), impulse.rising);
        return corrective == ElliottPhase.CORRECTIVE_C;
    }

    /**
     * @return underlying swing indicator used for phase detection
     * @since 0.22.0
     */
    public ElliottSwingIndicator getSwingIndicator() {
        return swingIndicator;
    }

    ElliottSwingMetadata metadata(final int index) {
        final List<ElliottSwing> swings = swingIndicator.getValue(index);
        return ElliottSwingMetadata.of(swings, numFactory);
    }

    ImpulseAssessment assessImpulse(final ElliottSwingMetadata metadata) {
        if (!metadata.isValid() || metadata.isEmpty()) {
            return new ImpulseAssessment(ElliottPhase.NONE, List.of(), 0, true);
        }
        List<ElliottSwing> segment = metadata.leading(5);
        ElliottPhase phase = evaluateImpulse(segment);
        boolean rising = !segment.isEmpty() && segment.get(0).isRising();
        int startIndex = 0;

        if (phase != ElliottPhase.WAVE5 && metadata.size() > 5) {
            final List<ElliottSwing> trailing = metadata.trailing(5);
            final ElliottPhase trailingPhase = evaluateImpulse(trailing);
            if (compareImpulse(trailingPhase, phase) > 0) {
                segment = trailing;
                phase = trailingPhase;
                rising = !segment.isEmpty() && segment.get(0).isRising();
                startIndex = metadata.size() - segment.size();
            }
        }

        return new ImpulseAssessment(phase, segment, startIndex, rising);
    }

    private ElliottPhase evaluateImpulse(final List<ElliottSwing> swings) {
        if (swings.isEmpty()) {
            return ElliottPhase.NONE;
        }
        final ElliottSwing wave1 = swings.get(0);
        if (!isFinite(wave1)) {
            return ElliottPhase.NONE;
        }
        final boolean rising = wave1.isRising();
        ElliottPhase phase = ElliottPhase.WAVE1;

        if (swings.size() < 2) {
            return phase;
        }

        final ElliottSwing wave2 = swings.get(1);
        if (!isWaveTwoValid(wave1, wave2, rising)) {
            return phase;
        }
        phase = ElliottPhase.WAVE2;

        if (swings.size() < 3) {
            return phase;
        }

        final ElliottSwing wave3 = swings.get(2);
        if (!isWaveThreeValid(wave1, wave2, wave3, rising)) {
            return phase;
        }
        phase = ElliottPhase.WAVE3;

        if (swings.size() < 4) {
            return phase;
        }

        final ElliottSwing wave4 = swings.get(3);
        if (!isWaveFourValid(wave1, wave3, wave4, rising)) {
            return phase;
        }
        phase = ElliottPhase.WAVE4;

        if (swings.size() < 5) {
            return phase;
        }

        final ElliottSwing wave5 = swings.get(4);
        if (!isWaveFiveValid(wave1, wave3, wave5, rising)) {
            return phase;
        }
        return ElliottPhase.WAVE5;
    }

    ElliottPhase evaluateCorrective(final ElliottSwingMetadata metadata, final List<ElliottSwing> correction,
            final boolean impulseRising) {
        if (correction.isEmpty()) {
            return ElliottPhase.NONE;
        }

        final ElliottSwing waveA = correction.get(0);
        if (!isFinite(waveA)) {
            return ElliottPhase.NONE;
        }
        if (waveA.isRising() == impulseRising) {
            return ElliottPhase.NONE;
        }

        ElliottPhase phase = ElliottPhase.CORRECTIVE_A;
        if (correction.size() < 2) {
            return phase;
        }

        final ElliottSwing waveB = correction.get(1);
        if (!isWaveBValid(waveA, waveB, impulseRising)) {
            return phase;
        }
        phase = ElliottPhase.CORRECTIVE_B;

        if (correction.size() < 3) {
            return phase;
        }

        final ElliottSwing waveC = correction.get(2);
        if (!isWaveCValid(waveA, waveC, impulseRising)) {
            return phase;
        }
        return ElliottPhase.CORRECTIVE_C;
    }

    boolean isWaveTwoValid(final ElliottSwing wave1, final ElliottSwing wave2, final boolean impulseRising) {
        if (!isFinite(wave2)) {
            return false;
        }
        if (wave1.isRising() == wave2.isRising()) {
            return false;
        }
        if (!fibonacciValidator.isWaveTwoRetracementValid(wave1, wave2)) {
            return false;
        }
        final Num boundary = wave1.fromPrice();
        final Num candidate = wave2.toPrice();
        if (candidate == null || boundary == null || candidate.isNaN() || boundary.isNaN()) {
            return false;
        }
        return impulseRising ? !candidate.isLessThan(boundary) : !candidate.isGreaterThan(boundary);
    }

    boolean isWaveThreeValid(final ElliottSwing wave1, final ElliottSwing wave2, final ElliottSwing wave3,
            final boolean impulseRising) {
        if (!isFinite(wave3)) {
            return false;
        }
        if (wave3.isRising() != impulseRising) {
            return false;
        }
        if (!fibonacciValidator.isWaveThreeExtensionValid(wave1, wave3)) {
            return false;
        }
        final Num candidate = wave3.toPrice();
        final Num boundary = wave1.toPrice();
        if (candidate == null || boundary == null || candidate.isNaN() || boundary.isNaN()) {
            return false;
        }
        return impulseRising ? candidate.isGreaterThan(boundary) : candidate.isLessThan(boundary);
    }

    boolean isWaveFourValid(final ElliottSwing wave1, final ElliottSwing wave3, final ElliottSwing wave4,
            final boolean impulseRising) {
        if (!isFinite(wave4)) {
            return false;
        }
        if (wave4.isRising() == impulseRising) {
            return false;
        }
        if (!fibonacciValidator.isWaveFourRetracementValid(wave3, wave4)) {
            return false;
        }
        final Num candidate = wave4.toPrice();
        final Num boundary = wave1.toPrice();
        if (candidate == null || boundary == null || candidate.isNaN() || boundary.isNaN()) {
            return false;
        }
        return impulseRising ? !candidate.isLessThan(boundary) : !candidate.isGreaterThan(boundary);
    }

    boolean isWaveFiveValid(final ElliottSwing wave1, final ElliottSwing wave3, final ElliottSwing wave5,
            final boolean impulseRising) {
        if (!isFinite(wave5)) {
            return false;
        }
        if (wave5.isRising() != impulseRising) {
            return false;
        }
        if (!fibonacciValidator.isWaveFiveProjectionValid(wave1, wave5)) {
            return false;
        }
        final Num candidate = wave5.toPrice();
        final Num boundary = wave3.toPrice();
        if (candidate == null || boundary == null || candidate.isNaN() || boundary.isNaN()) {
            return false;
        }
        return impulseRising ? candidate.isGreaterThan(boundary) : candidate.isLessThan(boundary);
    }

    boolean isWaveBValid(final ElliottSwing waveA, final ElliottSwing waveB, final boolean impulseRising) {
        if (!isFinite(waveB)) {
            return false;
        }
        if (waveB.isRising() != impulseRising) {
            return false;
        }
        if (!fibonacciValidator.isWaveBRetracementValid(waveA, waveB)) {
            return false;
        }
        final Num candidate = waveB.toPrice();
        final Num boundary = waveA.fromPrice();
        if (candidate == null || boundary == null || candidate.isNaN() || boundary.isNaN()) {
            return false;
        }
        return impulseRising ? !candidate.isGreaterThan(boundary) : !candidate.isLessThan(boundary);
    }

    boolean isWaveCValid(final ElliottSwing waveA, final ElliottSwing waveC, final boolean impulseRising) {
        if (!isFinite(waveC)) {
            return false;
        }
        if (waveC.isRising() == impulseRising) {
            return false;
        }
        if (!fibonacciValidator.isWaveCExtensionValid(waveA, waveC)) {
            return false;
        }
        final Num candidate = waveC.toPrice();
        final Num boundary = waveA.toPrice();
        if (candidate == null || boundary == null || candidate.isNaN() || boundary.isNaN()) {
            return false;
        }
        return impulseRising ? candidate.isLessThan(boundary) : candidate.isGreaterThan(boundary);
    }

    private boolean isFinite(final ElliottSwing swing) {
        if (swing == null) {
            return false;
        }
        final Num from = swing.fromPrice();
        final Num to = swing.toPrice();
        return from != null && to != null && !from.isNaN() && !to.isNaN() && !from.equals(NaN) && !to.equals(NaN);
    }

    private int compareImpulse(final ElliottPhase left, final ElliottPhase right) {
        return Integer.compare(left.impulseIndex(), right.impulseIndex());
    }

    static final class ImpulseAssessment {
        final ElliottPhase phase;
        final List<ElliottSwing> segment;
        final int startIndex;
        final boolean rising;

        ImpulseAssessment(final ElliottPhase phase, final List<ElliottSwing> segment, final int startIndex,
                final boolean rising) {
            this.phase = phase;
            this.segment = segment;
            this.startIndex = startIndex;
            this.rising = rising;
        }
    }
}
