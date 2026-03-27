/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.json;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.utils.DeprecationNotifier;

import java.time.Duration;
import java.time.Instant;

@Deprecated(since = "0.19", forRemoval = true)
public class GsonBarData {
    private long endTime;
    private Number openPrice;
    private Number highPrice;
    private Number lowPrice;
    private Number closePrice;
    private Number volume;
    private Number amount;

    public GsonBarData() {
        DeprecationNotifier.warnOnce(GsonBarData.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
    }

    @Deprecated(since = "0.19", forRemoval = true)
    public static GsonBarData from(Bar bar) {
        DeprecationNotifier.warnOnce(GsonBarData.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
        var result = new GsonBarData();
        result.endTime = bar.getEndTime().toEpochMilli();
        result.openPrice = bar.getOpenPrice().getDelegate();
        result.highPrice = bar.getHighPrice().getDelegate();
        result.lowPrice = bar.getLowPrice().getDelegate();
        result.closePrice = bar.getClosePrice().getDelegate();
        result.volume = bar.getVolume().getDelegate();
        result.amount = bar.getAmount().getDelegate();
        return result;
    }

    @Deprecated(since = "0.19", forRemoval = true)
    public void addTo(BaseBarSeries barSeries) {
        DeprecationNotifier.warnOnce(GsonBarData.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
        var endTimeInstant = Instant.ofEpochMilli(endTime);
        barSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endTimeInstant)
                .openPrice(openPrice)
                .highPrice(highPrice)
                .lowPrice(lowPrice)
                .closePrice(closePrice)
                .volume(volume)
                .amount(amount)
                .add();
    }
}
