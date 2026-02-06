/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.NumFactory;

/**
 * Tests for {@link ElliottFibonacciValidator}.
 */
class ElliottFibonacciValidatorTest {

    private NumFactory numFactory;
    private ElliottFibonacciValidator validator;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        validator = new ElliottFibonacciValidator(numFactory);
    }

    // ========== Wave 2 Retracement Tests (0.382 - 0.786) ==========

    @Test
    void waveTwoRetracement_atLowerBound_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 2 should retrace 38.2% = 3.82
        var wave1 = swing(0, 3, 100, 110);
        var wave2 = swing(3, 5, 110, 106.18); // amplitude 3.82
        assertThat(validator.isWaveTwoRetracementValid(wave1, wave2)).isTrue();
    }

    @Test
    void waveTwoRetracement_atUpperBound_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 2 should retrace 78.6% = 7.86
        var wave1 = swing(0, 3, 100, 110);
        var wave2 = swing(3, 5, 110, 102.14); // amplitude 7.86
        assertThat(validator.isWaveTwoRetracementValid(wave1, wave2)).isTrue();
    }

    @Test
    void waveTwoRetracement_atMiddle_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 2 retraces 50% = 5
        var wave1 = swing(0, 3, 100, 110);
        var wave2 = swing(3, 5, 110, 105); // amplitude 5
        assertThat(validator.isWaveTwoRetracementValid(wave1, wave2)).isTrue();
    }

    @Test
    void waveTwoRetracement_belowLowerBound_shouldBeInvalid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 2 retraces only 20% = 2 (below 38.2% - tolerance)
        var wave1 = swing(0, 3, 100, 110);
        var wave2 = swing(3, 5, 110, 108); // amplitude 2
        assertThat(validator.isWaveTwoRetracementValid(wave1, wave2)).isFalse();
    }

    @Test
    void waveTwoRetracement_aboveUpperBound_shouldBeInvalid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 2 retraces 90% = 9 (above 78.6% + tolerance)
        var wave1 = swing(0, 3, 100, 110);
        var wave2 = swing(3, 5, 110, 101); // amplitude 9
        assertThat(validator.isWaveTwoRetracementValid(wave1, wave2)).isFalse();
    }

    // ========== Wave 3 Extension Tests (1.0 - 2.618) ==========

    @Test
    void waveThreeExtension_atLowerBound_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 3 extends 100% = 10
        var wave1 = swing(0, 3, 100, 110);
        var wave3 = swing(5, 9, 105, 115); // amplitude 10
        assertThat(validator.isWaveThreeExtensionValid(wave1, wave3)).isTrue();
    }

    @Test
    void waveThreeExtension_atUpperBound_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 3 extends 261.8% = 26.18
        var wave1 = swing(0, 3, 100, 110);
        var wave3 = swing(5, 9, 105, 131.18); // amplitude 26.18
        assertThat(validator.isWaveThreeExtensionValid(wave1, wave3)).isTrue();
    }

    @Test
    void waveThreeExtension_at1618_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 3 extends 161.8% = 16.18
        var wave1 = swing(0, 3, 100, 110);
        var wave3 = swing(5, 9, 105, 121.18); // amplitude 16.18
        assertThat(validator.isWaveThreeExtensionValid(wave1, wave3)).isTrue();
    }

    @Test
    void waveThreeExtension_belowLowerBound_shouldBeInvalid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 3 extends only 80% = 8 (below 100% - tolerance)
        var wave1 = swing(0, 3, 100, 110);
        var wave3 = swing(5, 9, 105, 113); // amplitude 8
        assertThat(validator.isWaveThreeExtensionValid(wave1, wave3)).isFalse();
    }

    @Test
    void waveThreeExtension_aboveUpperBound_shouldBeInvalid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 3 extends 300% = 30 (above 261.8% + tolerance)
        var wave1 = swing(0, 3, 100, 110);
        var wave3 = swing(5, 9, 105, 135); // amplitude 30
        assertThat(validator.isWaveThreeExtensionValid(wave1, wave3)).isFalse();
    }

    // ========== Wave 4 Retracement Tests (0.236 - 0.786) ==========

    @Test
    void waveFourRetracement_atLowerBound_shouldBeValid() {
        // Wave 3: 105 -> 125 (amplitude 20)
        // Wave 4 retraces 23.6% = 4.72
        var wave3 = swing(5, 9, 105, 125);
        var wave4 = swing(9, 11, 125, 120.28); // amplitude 4.72
        assertThat(validator.isWaveFourRetracementValid(wave3, wave4)).isTrue();
    }

    @Test
    void waveFourRetracement_at382_shouldBeValid() {
        // Wave 3: 105 -> 125 (amplitude 20)
        // Wave 4 retraces 38.2% = 7.64
        var wave3 = swing(5, 9, 105, 125);
        var wave4 = swing(9, 11, 125, 117.36); // amplitude 7.64
        assertThat(validator.isWaveFourRetracementValid(wave3, wave4)).isTrue();
    }

    @Test
    void waveFourRetracement_belowLowerBound_shouldBeInvalid() {
        // Wave 3: 105 -> 125 (amplitude 20)
        // Wave 4 retraces only 10% = 2 (below 23.6% - tolerance)
        var wave3 = swing(5, 9, 105, 125);
        var wave4 = swing(9, 11, 125, 123); // amplitude 2
        assertThat(validator.isWaveFourRetracementValid(wave3, wave4)).isFalse();
    }

    // ========== Wave 5 Projection Tests (0.618 - 1.618) ==========

    @Test
    void waveFiveProjection_atLowerBound_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 5 projects 61.8% = 6.18
        var wave1 = swing(0, 3, 100, 110);
        var wave5 = swing(11, 15, 118, 124.18); // amplitude 6.18
        assertThat(validator.isWaveFiveProjectionValid(wave1, wave5)).isTrue();
    }

    @Test
    void waveFiveProjection_at100_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 5 projects 100% = 10
        var wave1 = swing(0, 3, 100, 110);
        var wave5 = swing(11, 15, 118, 128); // amplitude 10
        assertThat(validator.isWaveFiveProjectionValid(wave1, wave5)).isTrue();
    }

    @Test
    void waveFiveProjection_atUpperBound_shouldBeValid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 5 projects 161.8% = 16.18
        var wave1 = swing(0, 3, 100, 110);
        var wave5 = swing(11, 15, 118, 134.18); // amplitude 16.18
        assertThat(validator.isWaveFiveProjectionValid(wave1, wave5)).isTrue();
    }

    @Test
    void waveFiveProjection_belowLowerBound_shouldBeInvalid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 5 projects only 40% = 4 (below 61.8% - tolerance)
        var wave1 = swing(0, 3, 100, 110);
        var wave5 = swing(11, 15, 118, 122); // amplitude 4
        assertThat(validator.isWaveFiveProjectionValid(wave1, wave5)).isFalse();
    }

    @Test
    void waveFiveProjection_aboveUpperBound_shouldBeInvalid() {
        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 5 projects 200% = 20 (above 161.8% + tolerance)
        var wave1 = swing(0, 3, 100, 110);
        var wave5 = swing(11, 15, 118, 138); // amplitude 20
        assertThat(validator.isWaveFiveProjectionValid(wave1, wave5)).isFalse();
    }

    // ========== Wave B Retracement Tests (0.382 - 0.886) ==========

    @Test
    void waveBRetracement_atMiddle_shouldBeValid() {
        // Wave A: 125 -> 110 (amplitude 15)
        // Wave B retraces 50% = 7.5
        var waveA = swing(15, 18, 125, 110);
        var waveB = swing(18, 20, 110, 117.5); // amplitude 7.5
        assertThat(validator.isWaveBRetracementValid(waveA, waveB)).isTrue();
    }

    @Test
    void waveBRetracement_at618_shouldBeValid() {
        // Wave A: 125 -> 110 (amplitude 15)
        // Wave B retraces 61.8% = 9.27
        var waveA = swing(15, 18, 125, 110);
        var waveB = swing(18, 20, 110, 119.27); // amplitude 9.27
        assertThat(validator.isWaveBRetracementValid(waveA, waveB)).isTrue();
    }

    @Test
    void waveBRetracement_belowLowerBound_shouldBeInvalid() {
        // Wave A: 125 -> 110 (amplitude 15)
        // Wave B retraces only 20% = 3 (below 38.2% - tolerance)
        var waveA = swing(15, 18, 125, 110);
        var waveB = swing(18, 20, 110, 113); // amplitude 3
        assertThat(validator.isWaveBRetracementValid(waveA, waveB)).isFalse();
    }

    // ========== Wave C Extension Tests (1.0 - 1.618) ==========

    @Test
    void waveCExtension_at100_shouldBeValid() {
        // Wave A: 125 -> 110 (amplitude 15)
        // Wave C extends 100% = 15
        var waveA = swing(15, 18, 125, 110);
        var waveC = swing(20, 24, 118, 103); // amplitude 15
        assertThat(validator.isWaveCExtensionValid(waveA, waveC)).isTrue();
    }

    @Test
    void waveCExtension_at1272_shouldBeValid() {
        // Wave A: 125 -> 110 (amplitude 15)
        // Wave C extends 127.2% = 19.08
        var waveA = swing(15, 18, 125, 110);
        var waveC = swing(20, 24, 118, 98.92); // amplitude 19.08
        assertThat(validator.isWaveCExtensionValid(waveA, waveC)).isTrue();
    }

    @Test
    void waveCExtension_belowLowerBound_shouldBeInvalid() {
        // Wave A: 125 -> 110 (amplitude 15)
        // Wave C extends only 80% = 12 (below 100% - tolerance)
        var waveA = swing(15, 18, 125, 110);
        var waveC = swing(20, 24, 118, 106); // amplitude 12
        assertThat(validator.isWaveCExtensionValid(waveA, waveC)).isFalse();
    }

    @Test
    void waveCExtension_aboveUpperBound_shouldBeInvalid() {
        // Wave A: 125 -> 110 (amplitude 15)
        // Wave C extends 200% = 30 (above 161.8% + tolerance)
        var waveA = swing(15, 18, 125, 110);
        var waveC = swing(20, 24, 118, 88); // amplitude 30
        assertThat(validator.isWaveCExtensionValid(waveA, waveC)).isFalse();
    }

    // ========== Edge Case Tests ==========

    @Test
    void validation_withNaNAmplitude_shouldReturnFalse() {
        var wave1 = swing(0, 3, 100, 110);
        var waveNaN = new ElliottSwing(3, 5, numFactory.numOf(110), NaN.NaN, ElliottDegree.MINOR);

        assertThat(validator.isWaveTwoRetracementValid(wave1, waveNaN)).isFalse();
        assertThat(validator.isWaveThreeExtensionValid(wave1, waveNaN)).isFalse();
    }

    @Test
    void validation_withZeroDenominator_shouldReturnFalse() {
        var waveZero = swing(0, 3, 100, 100); // amplitude 0
        var wave2 = swing(3, 5, 100, 95);

        assertThat(validator.isWaveTwoRetracementValid(waveZero, wave2)).isFalse();
    }

    @Test
    void validation_withCustomTolerance_shouldRespectTolerance() {
        var strictValidator = new ElliottFibonacciValidator(numFactory, numFactory.numOf(0.01));

        // Wave 1: 100 -> 110 (amplitude 10)
        // Wave 2 retraces 35% = 3.5 (outside 38.2% - 0.01)
        var wave1 = swing(0, 3, 100, 110);
        var wave2 = swing(3, 5, 110, 106.5); // amplitude 3.5, ratio 0.35

        assertThat(strictValidator.isWaveTwoRetracementValid(wave1, wave2)).isFalse();

        // With default tolerance of 0.05, it would be valid
        assertThat(validator.isWaveTwoRetracementValid(wave1, wave2)).isTrue();
    }

    @Test
    void constructor_withNullNumFactory_shouldThrow() {
        assertThatThrownBy(() -> new ElliottFibonacciValidator(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_withNullTolerance_shouldThrow() {
        assertThatThrownBy(() -> new ElliottFibonacciValidator(numFactory, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ========== Bearish Wave Tests ==========

    @Test
    void waveTwoRetracement_bearish_shouldBeValid() {
        // Bearish Wave 1: 110 -> 100 (amplitude 10)
        // Wave 2 retraces upward 50% = 5
        var wave1 = swing(0, 3, 110, 100);
        var wave2 = swing(3, 5, 100, 105); // amplitude 5
        assertThat(validator.isWaveTwoRetracementValid(wave1, wave2)).isTrue();
    }

    @Test
    void waveThreeExtension_bearish_shouldBeValid() {
        // Bearish Wave 1: 110 -> 100 (amplitude 10)
        // Wave 3 extends downward 161.8% = 16.18
        var wave1 = swing(0, 3, 110, 100);
        var wave3 = swing(5, 9, 105, 88.82); // amplitude 16.18
        assertThat(validator.isWaveThreeExtensionValid(wave1, wave3)).isTrue();
    }

    // ========== Helper Methods ==========

    private ElliottSwing swing(int fromIndex, int toIndex, double fromPrice, double toPrice) {
        return new ElliottSwing(fromIndex, toIndex, numFactory.numOf(fromPrice), numFactory.numOf(toPrice),
                ElliottDegree.MINOR);
    }
}
