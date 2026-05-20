/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.json;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

class LegacyJsonBarSeriesPayload {

    private String name;
    private List<LegacyJsonBarDataPayload> ohlc = new LinkedList<>();

    static LegacyJsonBarSeriesPayload from(BarSeries series) {
        LegacyJsonBarSeriesPayload result = new LegacyJsonBarSeriesPayload();
        result.name = series.getName();
        List<Bar> barData = series.getBarData();
        for (Bar bar : barData) {
            result.ohlc.add(LegacyJsonBarDataPayload.from(bar));
        }
        return result;
    }

    static BarSeries toBarSeriesOrNull(LegacyJsonBarSeriesPayload payload) {
        return payload == null ? null : payload.toBarSeries();
    }

    BarSeries toBarSeries() {
        BaseBarSeries result = new BaseBarSeriesBuilder().withName(name).build();
        if (ohlc == null) {
            return result;
        }
        for (LegacyJsonBarDataPayload data : ohlc) {
            data.addTo(result);
        }
        return result;
    }

    void copyFrom(LegacyJsonBarSeriesPayload source) {
        this.name = source.name;
        this.ohlc = source.ohlc == null ? new LinkedList<>() : new LinkedList<>(source.ohlc);
    }

    static final class LegacyJsonBarDataPayload {

        private long endTime;
        private Number openPrice;
        private Number highPrice;
        private Number lowPrice;
        private Number closePrice;
        private Number volume;
        private Number amount;

        static LegacyJsonBarDataPayload from(Bar bar) {
            LegacyJsonBarDataPayload result = new LegacyJsonBarDataPayload();
            result.endTime = bar.getEndTime().toEpochMilli();
            result.openPrice = bar.getOpenPrice().getDelegate();
            result.highPrice = bar.getHighPrice().getDelegate();
            result.lowPrice = bar.getLowPrice().getDelegate();
            result.closePrice = bar.getClosePrice().getDelegate();
            result.volume = bar.getVolume().getDelegate();
            result.amount = bar.getAmount().getDelegate();
            return result;
        }

        void addTo(BaseBarSeries barSeries) {
            Instant endTimeInstant = Instant.ofEpochMilli(endTime);
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
}
