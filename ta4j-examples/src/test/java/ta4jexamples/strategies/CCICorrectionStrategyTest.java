/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.junit.Test;
import ta4jexamples.charting.display.SwingChartDisplayer;

public class CCICorrectionStrategyTest {

    @Test
    public void test() {
        // Disable chart display during tests to prevent windows from popping up
        // This works in both headless and non-headless environments
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            CCICorrectionStrategy.main(null);
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }
}
