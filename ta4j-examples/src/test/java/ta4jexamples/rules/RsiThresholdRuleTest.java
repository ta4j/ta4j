/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsiThresholdRuleTest {

    @Test
    void labelConstructorAndJsonRoundTripStayStable() {
        BarSeries series = new MockBarSeriesBuilder()
                .withData(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d, 11d, 12d, 13d, 14d, 15d, 16d, 17d, 18d, 19d, 20d)
                .build();
        RsiThresholdRule original = new RsiThresholdRule(series, "ABOVE", "14", "60");

        Rule restored = Rule.fromJson(series, original.toJson());

        assertEquals("RsiThresholdRule_ABOVE_14_60", original.getName());
        assertTrue(original.isSatisfied(series.getEndIndex()));
        assertEquals(original.getName(), restored.getName());
        assertTrue(restored.isSatisfied(series.getEndIndex()));
    }
}
