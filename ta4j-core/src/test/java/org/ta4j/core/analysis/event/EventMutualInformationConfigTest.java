/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.analysis.AnalysisContext;

public class EventMutualInformationConfigTest {

    @Test
    public void validatesTargetWindowOffsets() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationConfig(-1, 0, 2, BinningStrategy.EQUAL_WIDTH));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationConfig(3, 2, 2, BinningStrategy.EQUAL_WIDTH));
    }

    @Test
    public void validatesPredictorBinCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationConfig(0, 0, 1, BinningStrategy.EQUAL_WIDTH));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationConfig(0, 0,
                EventMutualInformationConfig.MAX_PREDICTOR_BIN_COUNT + 1, BinningStrategy.EQUAL_WIDTH));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationConfig(0, 0, Integer.MAX_VALUE, BinningStrategy.EQUAL_WIDTH));
    }

    @Test
    public void rejectsNullComponents() {
        assertThrows(NullPointerException.class, () -> new EventMutualInformationConfig(0, 0, 2, null));
        assertThrows(NullPointerException.class,
                () -> new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH, null));
    }

    @Test
    public void convenienceConstructorDefaultsToClampedHistory() {
        EventMutualInformationConfig config = new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH);

        assertEquals(AnalysisContext.MissingHistoryPolicy.CLAMP, config.historyPolicy());
        assertEquals(BinningStrategy.EQUAL_WIDTH, config.binningStrategy());
    }
}
