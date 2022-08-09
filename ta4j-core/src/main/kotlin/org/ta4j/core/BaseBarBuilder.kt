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
package org.ta4j.core

import org.ta4j.core.num.*
import java.time.Duration
import java.time.ZonedDateTime

open class BaseBarBuilder internal constructor() {
    private var timePeriod: Duration? = null
    private var endTime: ZonedDateTime? = null
    private var openPrice: Num? = null
    private var closePrice: Num? = null
    private var highPrice: Num? = null
    private var lowPrice: Num? = null
    private var amount: Num? = null
    private var volume: Num? = null
    private var trades: Long = 0
    open fun timePeriod(timePeriod: Duration?): BaseBarBuilder {
        this.timePeriod = timePeriod
        return this
    }

    open fun endTime(endTime: ZonedDateTime?): BaseBarBuilder {
        this.endTime = endTime
        return this
    }

    fun openPrice(openPrice: Num?): BaseBarBuilder {
        this.openPrice = openPrice
        return this
    }

    fun closePrice(closePrice: Num?): BaseBarBuilder {
        this.closePrice = closePrice
        return this
    }

    fun highPrice(highPrice: Num?): BaseBarBuilder {
        this.highPrice = highPrice
        return this
    }

    fun lowPrice(lowPrice: Num?): BaseBarBuilder {
        this.lowPrice = lowPrice
        return this
    }

    fun amount(amount: Num?): BaseBarBuilder {
        this.amount = amount
        return this
    }

    fun volume(volume: Num?): BaseBarBuilder {
        this.volume = volume
        return this
    }

    open fun trades(trades: Long): BaseBarBuilder {
        this.trades = trades
        return this
    }

    fun build(): BaseBar {
        return BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades)
    }
}