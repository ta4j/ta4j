/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

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

    private static final int IMPULSE_LENGTH = 5;
    private static final int CORRECTION_LENGTH = 3;

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
    }

    private static NumFactory requireFactory(final ElliottSwingIndicator swingIndicator) {
        return Objects.requireNonNull(swingIndicator, "swingIndicator").getBarSeries().numFactory();
    }

    @Override
    protected ElliottPhase calculate(final int index) {
        final ElliottSwingMetadata metadata = metadata(index);
        if (!metadata.isValid() || metadata.isEmpty()) {
            return ElliottPhase.NONE;
        }

        final CycleAssessment cycle = assessCycle(metadata);
        final ImpulseAssessment impulse = cycle.impulse;
        if (!impulse.phase().isImpulse()) {
            return ElliottPhase.NONE;
        }

        if (cycle.correctivePhase.isCorrective()) {
            return cycle.correctivePhase;
        }
        return impulse.phase();
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
        return List.copyOf(assessCycle(metadata).impulse.segment());
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
        final CycleAssessment cycle = assessCycle(metadata);
        if (cycle.impulse.phase() != ElliottPhase.WAVE5 || !cycle.correctivePhase.isCorrective()) {
            return List.of();
        }
        final int length = cycle.correctivePhase.correctiveIndex();
        return cycle.correction.subList(0, Math.min(length, cycle.correction.size()));
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
        return assessCycle(metadata).impulse.phase() == ElliottPhase.WAVE5;
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
        return assessCycle(metadata).correctivePhase == ElliottPhase.CORRECTIVE_C;
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
        return ElliottSwingMetadata.of(swings, swingIndicator.getBarSeries().numFactory());
    }

    ImpulseAssessment assessImpulse(final ElliottSwingMetadata metadata) {
        return assessCycle(metadata).impulse;
    }

    private CycleAssessment assessCycle(final ElliottSwingMetadata metadata) {
        int startIndex = 0;
        int maxIterations = metadata.size(); // Prevent infinite loops
        int iterations = 0;
        while (true) {
            if (iterations++ >= maxIterations) {
                return new CycleAssessment(new ImpulseAssessment(ElliottPhase.NONE, List.of(), startIndex, true),
                        List.of(), ElliottPhase.NONE);
            }
            final ImpulseAssessment impulse = assessImpulseAt(metadata, startIndex);
            if (impulse.phase() != ElliottPhase.WAVE5) {
                return new CycleAssessment(impulse, List.of(), ElliottPhase.NONE);
            }

            final int correctionStart = startIndex + impulse.segment().size();
            if (metadata.size() <= correctionStart) {
                return new CycleAssessment(impulse, List.of(), ElliottPhase.NONE);
            }
            final int correctionEnd = Math.min(correctionStart + CORRECTION_LENGTH, metadata.size());
            final List<ElliottSwing> correction = metadata.subList(correctionStart, correctionEnd);
            final ElliottPhase correctivePhase = evaluateCorrective(correction, impulse.rising());

            if (correctivePhase == ElliottPhase.CORRECTIVE_C && metadata.size() > correctionEnd) {
                startIndex = correctionEnd;
                continue;
            }

            if (!correctivePhase.isCorrective()) {
                return new CycleAssessment(impulse, List.of(), ElliottPhase.NONE);
            }

            return new CycleAssessment(impulse, correction, correctivePhase);
        }
    }

    private ImpulseAssessment assessImpulseAt(final ElliottSwingMetadata metadata, final int startIndex) {
        if (!metadata.isValid() || metadata.isEmpty() || startIndex < 0 || startIndex >= metadata.size()) {
            return new ImpulseAssessment(ElliottPhase.NONE, List.of(), startIndex, true);
        }
        final int endIndex = Math.min(startIndex + IMPULSE_LENGTH, metadata.size());
        final List<ElliottSwing> segment = metadata.subList(startIndex, endIndex);
        final ElliottPhase phase = evaluateImpulse(segment);
        final boolean rising = !segment.isEmpty() && segment.get(0).isRising();
        return new ImpulseAssessment(phase, segment, startIndex, rising);
    }

    private ElliottPhase evaluateImpulse(final List<ElliottSwing> swings) {
        if (swings.isEmpty()) {
            return ElliottPhase.NONE;
        }
        final ElliottSwing wave1 = swings.get(0);
        if (!isValidSwing(wave1)) {
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
        if (!isWaveThreeValid(wave1, wave3, rising)) {
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

    ElliottPhase evaluateCorrective(final List<ElliottSwing> correction, final boolean impulseRising) {
        if (correction.isEmpty()) {
            return ElliottPhase.NONE;
        }

        final ElliottSwing waveA = correction.get(0);
        if (!isValidSwing(waveA)) {
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
        if (!isValidSwing(wave2)) {
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
        if (!Num.isValid(candidate) || !Num.isValid(boundary)) {
            return false;
        }
        return impulseRising ? !candidate.isLessThan(boundary) : !candidate.isGreaterThan(boundary);
    }

    boolean isWaveThreeValid(final ElliottSwing wave1, final ElliottSwing wave3, final boolean impulseRising) {
        if (!isValidSwing(wave3)) {
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
        if (!Num.isValid(candidate) || !Num.isValid(boundary)) {
            return false;
        }
        return impulseRising ? candidate.isGreaterThan(boundary) : candidate.isLessThan(boundary);
    }

    boolean isWaveFourValid(final ElliottSwing wave1, final ElliottSwing wave3, final ElliottSwing wave4,
            final boolean impulseRising) {
        if (!isValidSwing(wave4)) {
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
        if (!Num.isValid(candidate) || !Num.isValid(boundary)) {
            return false;
        }
        return impulseRising ? !candidate.isLessThan(boundary) : !candidate.isGreaterThan(boundary);
    }

    boolean isWaveFiveValid(final ElliottSwing wave1, final ElliottSwing wave3, final ElliottSwing wave5,
            final boolean impulseRising) {
        if (!isValidSwing(wave5)) {
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
        if (!Num.isValid(candidate) || !Num.isValid(boundary)) {
            return false;
        }
        return impulseRising ? candidate.isGreaterThan(boundary) : candidate.isLessThan(boundary);
    }

    boolean isWaveBValid(final ElliottSwing waveA, final ElliottSwing waveB, final boolean impulseRising) {
        if (!isValidSwing(waveB)) {
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
        if (!Num.isValid(candidate) || !Num.isValid(boundary)) {
            return false;
        }
        return impulseRising ? !candidate.isGreaterThan(boundary) : !candidate.isLessThan(boundary);
    }

    boolean isWaveCValid(final ElliottSwing waveA, final ElliottSwing waveC, final boolean impulseRising) {
        if (!isValidSwing(waveC)) {
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
        if (!Num.isValid(candidate) || !Num.isValid(boundary)) {
            return false;
        }
        return impulseRising ? candidate.isLessThan(boundary) : candidate.isGreaterThan(boundary);
    }

    private boolean isValidSwing(final ElliottSwing swing) {
        if (swing == null) {
            return false;
        }
        return Num.isValid(swing.fromPrice()) && Num.isValid(swing.toPrice());
    }

    private record CycleAssessment(ImpulseAssessment impulse, List<ElliottSwing> correction,
            ElliottPhase correctivePhase) {
    }

    record ImpulseAssessment(ElliottPhase phase, List<ElliottSwing> segment, int startIndex, boolean rising) {
    }
}
