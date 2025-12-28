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
