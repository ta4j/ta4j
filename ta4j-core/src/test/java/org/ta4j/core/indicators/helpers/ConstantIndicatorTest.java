/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class ConstantIndicatorTest {
    private ConstantIndicator<Num> constantIndicator;

    @BeforeEach
    public void setUp() {
        var series = new MockBarSeriesBuilder().build();
        constantIndicator = new ConstantIndicator<>(series, series.numFactory().numOf(30.33));
    }

    @Test
    public void constantIndicator() {
        assertNumEquals("30.33", constantIndicator.getValue(0));
        assertNumEquals("30.33", constantIndicator.getValue(1));
        assertNumEquals("30.33", constantIndicator.getValue(10));
        assertNumEquals("30.33", constantIndicator.getValue(30));
    }
}
