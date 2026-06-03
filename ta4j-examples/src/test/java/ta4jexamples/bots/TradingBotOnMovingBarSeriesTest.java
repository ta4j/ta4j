/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.bots;

import java.time.Duration;

import org.junit.Test;

public class TradingBotOnMovingBarSeriesTest {

    @Test
    public void test() throws InterruptedException {
        TradingBotOnMovingBarSeries.runSimulation(50, Duration.ZERO);
    }
}
