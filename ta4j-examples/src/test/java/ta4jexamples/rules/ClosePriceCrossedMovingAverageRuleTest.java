/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosePriceCrossedMovingAverageRuleTest {

    @Test
    void labelConstructorAndJsonRoundTripStayStable() {
        BarSeries series = new MockBarSeriesBuilder().withData(12d, 11d, 10d, 9d, 8d, 9d, 10d, 11d, 12d).build();
        ClosePriceCrossedMovingAverageRule original = new ClosePriceCrossedMovingAverageRule(series, "UP", "SMA", "3");

        Rule restored = Rule.fromJson(series, original.toJson());
        boolean originalSatisfied = IntStream.rangeClosed(series.getBeginIndex(), series.getEndIndex())
                .anyMatch(original::isSatisfied);
        boolean restoredSatisfied = IntStream.rangeClosed(series.getBeginIndex(), series.getEndIndex())
                .anyMatch(restored::isSatisfied);

        assertEquals("ClosePriceCrossedMovingAverageRule_UP_SMA_3", original.getName());
        assertTrue(originalSatisfied);
        assertEquals(original.getName(), restored.getName());
        assertTrue(restoredSatisfied);
    }
}
