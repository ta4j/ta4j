/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.live;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

/**
 * Live trading implementation of a {@link Bar}.
 */
record LiveBar(
    Duration timePeriod,
    Instant beginTime,
    Instant endTime,
    Num openPrice,
    Num highPrice,
    Num lowPrice,
    Num closePrice,
    Num volume
) implements Bar {


  @Override
  public String toString() {
    return String.format(
        "{end time: %1s, close price: %2$f, open price: %3$f, low price: %4$f, high price: %5$f, volume: %6$f}",
        this.endTime.atZone(ZoneId.systemDefault()),
        this.closePrice.doubleValue(),
        this.openPrice.doubleValue(),
        this.lowPrice.doubleValue(),
        this.highPrice.doubleValue(),
        this.volume.doubleValue()
    );
  }
}
