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

import org.junit.jupiter.api.Test;

class ScenarioTypeTest {

    @Test
    void impulseTypeProperties() {
        assertThat(ScenarioType.IMPULSE.isImpulse()).isTrue();
        assertThat(ScenarioType.IMPULSE.isCorrective()).isFalse();
        assertThat(ScenarioType.IMPULSE.expectedWaveCount()).isEqualTo(5);
    }

    @Test
    void zigzagTypeProperties() {
        assertThat(ScenarioType.CORRECTIVE_ZIGZAG.isImpulse()).isFalse();
        assertThat(ScenarioType.CORRECTIVE_ZIGZAG.isCorrective()).isTrue();
        assertThat(ScenarioType.CORRECTIVE_ZIGZAG.expectedWaveCount()).isEqualTo(3);
    }

    @Test
    void flatTypeProperties() {
        assertThat(ScenarioType.CORRECTIVE_FLAT.isImpulse()).isFalse();
        assertThat(ScenarioType.CORRECTIVE_FLAT.isCorrective()).isTrue();
        assertThat(ScenarioType.CORRECTIVE_FLAT.expectedWaveCount()).isEqualTo(3);
    }

    @Test
    void triangleTypeProperties() {
        assertThat(ScenarioType.CORRECTIVE_TRIANGLE.isImpulse()).isFalse();
        assertThat(ScenarioType.CORRECTIVE_TRIANGLE.isCorrective()).isTrue();
        assertThat(ScenarioType.CORRECTIVE_TRIANGLE.expectedWaveCount()).isEqualTo(5);
    }

    @Test
    void unknownTypeProperties() {
        assertThat(ScenarioType.UNKNOWN.isImpulse()).isFalse();
        assertThat(ScenarioType.UNKNOWN.isCorrective()).isFalse();
        assertThat(ScenarioType.UNKNOWN.expectedWaveCount()).isZero();
    }
}
