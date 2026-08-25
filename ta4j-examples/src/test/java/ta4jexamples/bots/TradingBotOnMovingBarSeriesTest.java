/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.bots;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.num.DecimalNumFactory;

public class TradingBotOnMovingBarSeriesTest {

    @Test
    public void rejectsNegativeIterationCountWithoutStartingSimulation() {
        assertThrows(IllegalArgumentException.class,
                () -> TradingBotOnMovingBarSeries.runSimulation(-1, Duration.ZERO));
    }

    @Test
    public void rejectsNullTickDelayWithoutStartingSimulation() {
        assertThrows(IllegalArgumentException.class, () -> TradingBotOnMovingBarSeries.runSimulation(0, null));
    }

    @Test
    public void rejectsNegativeTickDelayWithoutStartingSimulation() {
        assertThrows(IllegalArgumentException.class,
                () -> TradingBotOnMovingBarSeries.runSimulation(0, Duration.ofMillis(-1)));
    }

    @Test
    public void runsOneIterationWithInjectedSeriesAndBar() throws InterruptedException {
        BarSeries series = fixtureSeries();

        TradingBotOnMovingBarSeries.runSimulation(1, Duration.ZERO, () -> series,
                TradingBotOnMovingBarSeriesTest::fixtureBar);

        assertEquals(13, series.getBarCount());
    }

    private static BarSeries fixtureSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("test-series").build();
        Duration period = Duration.ofDays(1);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        for (int index = 0; index < 12; index++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(start.plus(period.multipliedBy(index + 1L)))
                    .openPrice(100 + index)
                    .highPrice(102 + index)
                    .lowPrice(99 + index)
                    .closePrice(101 + index)
                    .volume(1)
                    .add();
        }
        return series;
    }

    private static Bar fixtureBar() {
        return new TimeBarBuilder(DecimalNumFactory.getInstance()).amount(1)
                .volume(1)
                .timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-14T00:00:00Z"))
                .openPrice(112)
                .highPrice(114)
                .lowPrice(111)
                .closePrice(113)
                .build();
    }
}
