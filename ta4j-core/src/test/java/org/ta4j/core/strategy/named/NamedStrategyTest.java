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
package org.ta4j.core.strategy.named;

import org.junit.jupiter.api.Test;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamedStrategyTest {

    @Test
    void buildLabelRejectsUnderscoreParameters() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NamedStrategy.buildLabel(NamedStrategyFixture.class, "fast", "slow_value", "u3"));

        assertEquals("Named strategy parameters cannot contain underscores: parameters[1]", ex.getMessage());
    }

    @Test
    void buildLabelRejectsNullParameters() {
        assertThrows(NullPointerException.class,
                () -> NamedStrategy.buildLabel(NamedStrategyFixture.class, "fast", null));
    }

    @Test
    void varargsConstructorStillAcceptedWithDelimiterFreeParameters() {
        var series = new MockBarSeriesBuilder().withData(1d).build();

        new NamedStrategyFixture(series, "1.0", "u3");
    }
}