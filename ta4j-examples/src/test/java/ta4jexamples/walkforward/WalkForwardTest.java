/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package ta4jexamples.walkforward;

import org.junit.Test;
import ta4jexamples.charting.display.SwingChartDisplayer;

public class WalkForwardTest {

    @Test
    public void test() {
        // Disable chart display during tests to prevent windows from popping up
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            WalkForward.main(null);
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }
}
