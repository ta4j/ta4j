/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

public class PortfolioCorrelationAnalysisTest {

    @Test
    public void normalizesToOneMidnightBarPerUtcDateKeepingTheLastBar() {
        BarSeries raw = new BaseBarSeriesBuilder().withName("raw").build();
        addBar(raw, "2026-03-02T14:30:00Z", 10);
        addBar(raw, "2026-03-02T21:00:00Z", 11);
        addBar(raw, "2026-03-03T21:00:00Z", 12);

        BarSeries normalized = PortfolioCorrelationAnalysis.normalizeDailySeries("SPY", raw);

        assertEquals("SPY", normalized.getName());
        assertEquals(2, normalized.getBarCount());
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), normalized.getBar(0).getEndTime());
        assertEquals(Instant.parse("2026-03-03T00:00:00Z"), normalized.getBar(1).getEndTime());
        assertEquals(Duration.ofDays(1), normalized.getBar(0).getTimePeriod());
        assertEquals(11, normalized.getBar(0).getClosePrice().intValue());
        assertEquals(12, normalized.getBar(1).getClosePrice().intValue());
    }

    private static void addBar(BarSeries series, String endTime, double close) {
        series.barBuilder()
                .timePeriod(Duration.ofHours(1))
                .endTime(Instant.parse(endTime))
                .openPrice(close)
                .highPrice(close)
                .lowPrice(close)
                .closePrice(close)
                .volume(0)
                .add();
    }
}
