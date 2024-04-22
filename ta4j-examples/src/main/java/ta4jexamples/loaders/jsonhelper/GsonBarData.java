/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.backtest.BacktestBar;
import org.ta4j.core.backtest.BacktestBarSeries;

public class GsonBarData {
    private long endTime;
    private Number openPrice;
    private Number highPrice;
    private Number lowPrice;
    private Number closePrice;
    private Number volume;
    private Number amount;

    public static GsonBarData from(final BacktestBar bar) {
        final var result = new GsonBarData();
        result.endTime = bar.endTime().toInstant().toEpochMilli();
        result.openPrice = bar.openPrice().getDelegate();
        result.highPrice = bar.highPrice().getDelegate();
        result.lowPrice = bar.lowPrice().getDelegate();
        result.closePrice = bar.closePrice().getDelegate();
        result.volume = bar.volume().getDelegate();
        result.amount = bar.getAmount().getDelegate();
        return result;
    }

    public void addTo(final BacktestBarSeries barSeries) {
        final var endTimeInstant = Instant.ofEpochMilli(this.endTime);
        final var endBarTime = ZonedDateTime.ofInstant(endTimeInstant, ZoneId.systemDefault());
        barSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endBarTime)
                .openPrice(this.openPrice)
                .highPrice(this.highPrice)
                .lowPrice(this.lowPrice)
                .closePrice(this.closePrice)
                .volume(this.volume)
                .amount(this.amount)
                .add();
    }
}
