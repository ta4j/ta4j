/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core.mocks

import org.ta4j.core.BaseBar
import org.ta4j.core.num.Num
import java.time.Duration
import java.time.ZonedDateTime
import java.util.function.Function

/**
 * A mock bar with sample data.
 */
class MockBar : BaseBar {
    override var trades: Long = 0

    constructor(closePrice: Double, numFunction: Function<Number?, Num>) : this(
        ZonedDateTime.now(),
        closePrice,
        numFunction
    ) {
    }

    constructor(closePrice: Double, volume: Double, numFunction: Function<Number?, Num>) : super(
        Duration.ofDays(1),
        ZonedDateTime.now(),
        0.0,
        0.0,
        0.0,
        closePrice,
        volume,
        0.0,
        0,
        numFunction
    ) {
    }

    constructor(
        endTime: ZonedDateTime?,
        closePrice: Double,
        numFunction: Function<Number?, Num>
    ) : super(Duration.ofDays(1), endTime, 0.0, 0.0, 0.0, closePrice, 0.0, 0.0, 0, numFunction) {
    }

    constructor(
        endTime: ZonedDateTime?,
        closePrice: Double,
        volume: Double,
        numFunction: Function<Number?, Num>
    ) : super(
        Duration.ofDays(1), endTime, 0.0, 0.0, 0.0, closePrice, volume, 0.0, 0, numFunction
    ) {
    }

    constructor(
        openPrice: Double, closePrice: Double, highPrice: Double, lowPrice: Double,
        numFunction: Function<Number?, Num>
    ) : super(
        Duration.ofDays(1), ZonedDateTime.now(), openPrice, highPrice, lowPrice, closePrice, 1.0, 0.0, 0,
        numFunction
    ) {
    }

    constructor(
        openPrice: Double, closePrice: Double, highPrice: Double, lowPrice: Double, volume: Double,
        numFunction: Function<Number?, Num>
    ) : super(
        Duration.ofDays(1), ZonedDateTime.now(), openPrice, highPrice, lowPrice, closePrice, volume, 0.0, 0,
        numFunction
    ) {
    }

    constructor(
        endTime: ZonedDateTime?, openPrice: Double, closePrice: Double, highPrice: Double, lowPrice: Double,
        amount: Double, volume: Double, trades: Long, numFunction: Function<Number?, Num>
    ) : super(
        Duration.ofDays(1),
        endTime,
        openPrice,
        highPrice,
        lowPrice,
        closePrice,
        volume,
        amount,
        0,
        numFunction
    ) {
        this.trades = trades
    }

    companion object {
        private const val serialVersionUID = -4546486893163810212L
    }
}