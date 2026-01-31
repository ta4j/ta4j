/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottConfidenceTest {

    private final NumFactory numFactory = DecimalNumFactory.getInstance();

    @Test
    void highConfidenceThreshold() {
        ElliottConfidence high = createConfidence(0.8);
        assertThat(high.isHighConfidence()).isTrue();
        assertThat(high.isLowConfidence()).isFalse();
    }

    @Test
    void lowConfidenceThreshold() {
        ElliottConfidence low = createConfidence(0.2);
        assertThat(low.isHighConfidence()).isFalse();
        assertThat(low.isLowConfidence()).isTrue();
    }

    @Test
    void mediumConfidence() {
        ElliottConfidence medium = createConfidence(0.5);
        assertThat(medium.isHighConfidence()).isFalse();
        assertThat(medium.isLowConfidence()).isFalse();
    }

    @Test
    void customThreshold() {
        ElliottConfidence confidence = createConfidence(0.6);
        assertThat(confidence.isAboveThreshold(0.5)).isTrue();
        assertThat(confidence.isAboveThreshold(0.7)).isFalse();
    }

    @Test
    void asPercentage() {
        ElliottConfidence confidence = createConfidence(0.75);
        assertThat(confidence.asPercentage()).isCloseTo(75.0, within(0.01));
    }

    @Test
    void zeroConfidence() {
        ElliottConfidence zero = ElliottConfidence.zero(numFactory);
        assertThat(zero.overall().doubleValue()).isZero();
        assertThat(zero.isValid()).isTrue();
        assertThat(zero.isLowConfidence()).isTrue();
        assertThat(zero.primaryReason()).isEqualTo("No valid structure");
    }

    @Test
    void weakestFactorIdentification() {
        Num high = numFactory.numOf(0.9);
        Num mid = numFactory.numOf(0.6);
        Num low = numFactory.numOf(0.3);

        ElliottConfidence confidence = new ElliottConfidence(high, high, low, mid, mid, mid, "Test");

        assertThat(confidence.weakestFactor()).isEqualTo("Time proportions");
    }

    @Test
    void validityCheck() {
        ElliottConfidence valid = createConfidence(0.5);
        assertThat(valid.isValid()).isTrue();

        ElliottConfidence invalid = new ElliottConfidence(NaN.NaN, numFactory.zero(), numFactory.zero(),
                numFactory.zero(), numFactory.zero(), numFactory.zero(), "Invalid");
        assertThat(invalid.isValid()).isFalse();
    }

    private ElliottConfidence createConfidence(double overall) {
        Num score = numFactory.numOf(overall);
        return new ElliottConfidence(score, score, score, score, score, score, "Test reason");
    }
}
