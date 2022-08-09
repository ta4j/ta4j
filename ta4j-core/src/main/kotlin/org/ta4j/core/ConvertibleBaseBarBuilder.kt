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
import java.util.function.Function

class ConvertibleBaseBarBuilder<T>(private val conversionFunction: Function<T, Num?>) : BaseBarBuilder() {
    override fun timePeriod(timePeriod: Duration?): ConvertibleBaseBarBuilder<T> {
        super.timePeriod(timePeriod)
        return this
    }

    override fun endTime(endTime: ZonedDateTime?): ConvertibleBaseBarBuilder<T> {
        super.endTime(endTime)
        return this
    }

    override fun trades(trades: Long): ConvertibleBaseBarBuilder<T> {
        super.trades(trades)
        return this
    }

    fun openPrice(openPrice: T): ConvertibleBaseBarBuilder<T> {
        super.openPrice(conversionFunction.apply(openPrice))
        return this
    }

    fun highPrice(highPrice: T): ConvertibleBaseBarBuilder<T> {
        super.highPrice(conversionFunction.apply(highPrice))
        return this
    }

    fun lowPrice(lowPrice: T): ConvertibleBaseBarBuilder<T> {
        super.lowPrice(conversionFunction.apply(lowPrice))
        return this
    }

    fun closePrice(closePrice: T): ConvertibleBaseBarBuilder<T> {
        super.closePrice(conversionFunction.apply(closePrice))
        return this
    }

    fun amount(amount: T): ConvertibleBaseBarBuilder<T> {
        super.amount(conversionFunction.apply(amount))
        return this
    }

    fun volume(volume: T): ConvertibleBaseBarBuilder<T> {
        super.volume(conversionFunction.apply(volume))
        return this
    }
}