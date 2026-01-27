/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ta4jexamples.datasources.json;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;

import java.time.Duration;
import java.time.Instant;

@Deprecated(since = "0.19")
public class GsonBarData {
    private long endTime;
    private Number openPrice;
    private Number highPrice;
    private Number lowPrice;
    private Number closePrice;
    private Number volume;
    private Number amount;

    @Deprecated
    public static GsonBarData from(Bar bar) {
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

    @Deprecated
    public void addTo(BaseBarSeries barSeries) {
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
