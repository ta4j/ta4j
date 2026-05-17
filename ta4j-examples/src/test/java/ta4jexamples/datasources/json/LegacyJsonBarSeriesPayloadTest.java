/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import com.google.gson.Gson;

class LegacyJsonBarSeriesPayloadTest {

    @Test
    void payloadRoundTripsLegacyJsonShape() {
        BarSeries series = new BaseBarSeriesBuilder().withName("legacy-json-series").build();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-01T00:00:00Z"))
                .openPrice(100)
                .highPrice(110)
                .lowPrice(90)
                .closePrice(105)
                .volume(1000)
                .amount(105000)
                .add();
        Gson gson = new Gson();

        String json = gson.toJson(LegacyJsonBarSeriesPayload.from(series));
        LegacyJsonBarSeriesPayload restoredPayload = gson.fromJson(json, LegacyJsonBarSeriesPayload.class);
        BarSeries restoredSeries = restoredPayload.toBarSeries();

        assertEquals(series.getName(), restoredSeries.getName());
        assertEquals(series.getBarCount(), restoredSeries.getBarCount());
        assertEquals(series.getFirstBar().getClosePrice(), restoredSeries.getFirstBar().getClosePrice());
        assertEquals(series.getFirstBar().getEndTime(), restoredSeries.getFirstBar().getEndTime());
    }
}
