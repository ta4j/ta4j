/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package ta4jexamples.loaders.jsonhelper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;

public class GsonBarData {
    private long endTime;
    private Number openPrice;
    private Number highPrice;
    private Number lowPrice;
    private Number closePrice;
    private Number volume;
    private Number amount;

    public static GsonBarData from(Bar bar) {
        GsonBarData result = new GsonBarData();
        result.endTime = bar.getEndTime().toInstant().toEpochMilli();
        result.openPrice = bar.getOpenPrice().getDelegate();
        result.highPrice = bar.getHighPrice().getDelegate();
        result.lowPrice = bar.getLowPrice().getDelegate();
        result.closePrice = bar.getClosePrice().getDelegate();
        result.volume = bar.getVolume().getDelegate();
        result.amount = bar.getAmount().getDelegate();
        return result;
    }

    public void addTo(BaseBarSeries barSeries) {
        Instant endTimeInstant = Instant.ofEpochMilli(endTime);
        ZonedDateTime endBarTime = ZonedDateTime.ofInstant(endTimeInstant, ZoneId.systemDefault());
        barSeries.addBar(endBarTime, openPrice, highPrice, lowPrice, closePrice, volume, amount);
    }
}
