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
package org.ta4j.core.indicators.numeric

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.Rule
import org.ta4j.core.indicators.EMAIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.*
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import org.ta4j.core.num.*
import org.ta4j.core.rules.CrossedDownIndicatorRule
import org.ta4j.core.rules.CrossedUpIndicatorRule
import org.ta4j.core.rules.OverIndicatorRule
import org.ta4j.core.rules.UnderIndicatorRule

/**
 * NumericIndicator is a "fluent decorator" for Indicator<Num>. It provides
 * methods to create rules and other "lightweight" indicators, using a
 * (hopefully) natural-looking and expressive series of method calls.
 *
 *
 * Methods like plus(), minus() and sqrt() correspond directly to methods in the
 * Num interface. These methods create "lightweight" (not cached) indicators to
 * add, subtract, etc. Many methods are overloaded to accept either
 * Indicator<Num> or Number arguments.
</Num> *
 *
 * Methods like sma() and ema() simply create the corresponding indicator
 * objects, (SMAIndicator or EMAIndicator, for example) with "this" as the first
 * argument. These methods usually instantiate cached objects.
 *
 *
 * Another set of methods, like crossedOver() and isGreaterThan() create Rule
 * objects. These are also overloaded to accept both Indicator<Num> and Number
 * arguments.
</Num></Num> */
class NumericIndicator protected constructor(protected val delegate: Indicator<Num>) : Indicator<Num> {
    fun delegate(): Indicator<Num> {
        return delegate
    }

    operator fun plus(other: Indicator<Num>): NumericIndicator {
        return of(BinaryOperation.sum(this, other))
    }

    operator fun plus(n: Number): NumericIndicator {
        return plus(createConstant(n))
    }

    operator fun minus(other: Indicator<Num>): NumericIndicator {
        return of(BinaryOperation.difference(this, other))
    }

    operator fun minus(n: Number): NumericIndicator {
        return minus(createConstant(n))
    }

    fun multipliedBy(other: Indicator<Num>): NumericIndicator {
        return of(BinaryOperation.product(this, other))
    }

    operator fun times(other: Indicator<Num>): NumericIndicator=multipliedBy(other)

    fun multipliedBy(n: Number): NumericIndicator {
        return multipliedBy(createConstant(n))
    }

    operator fun times(n: Number): NumericIndicator=multipliedBy(n)

    fun dividedBy(other: Indicator<Num>): NumericIndicator {
        return of(BinaryOperation.Companion.quotient(this, other))
    }

    operator fun div(other: Indicator<Num>): NumericIndicator =dividedBy(other)

    fun dividedBy(n: Number): NumericIndicator {
        return dividedBy(createConstant(n))
    }

    operator fun div(n: Number): NumericIndicator = dividedBy(n)

    fun min(other: Indicator<Num>): NumericIndicator {
        return of(BinaryOperation.Companion.min(this, other))
    }

    fun min(n: Number): NumericIndicator {
        return min(createConstant(n))
    }

    fun max(other: Indicator<Num>): NumericIndicator {
        return of(BinaryOperation.max(this, other))
    }

    fun max(n: Number): NumericIndicator {
        return max(createConstant(n))
    }

    fun abs(): NumericIndicator {
        return of(UnaryOperation.abs(this))
    }

    fun sqrt(): NumericIndicator {
        return of(UnaryOperation.sqrt(this))
    }

    fun squared(): NumericIndicator {
        // TODO: implement pow(n); a few others
        return this.times(this)
    }

    fun sma(n: Int): NumericIndicator {
        return of(SMAIndicator(this, n))
    }

    fun ema(n: Int): NumericIndicator {
        return of(EMAIndicator(this, n))
    }

    fun stddev(n: Int): NumericIndicator {
        return of(StandardDeviationIndicator(this, n))
    }

    fun highest(n: Int): NumericIndicator {
        return of(HighestValueIndicator(this, n))
    }

    fun lowest(n: Int): NumericIndicator {
        return of(LowestValueIndicator(this, n))
    }

    fun previous(n: Int): NumericIndicator {
        return of(PreviousValueIndicator(this, n))
    }

    fun previous(): Indicator<Num> {
        return previous(1)
    }

    fun crossedOver(other: Indicator<Num>): Rule {
        return CrossedUpIndicatorRule(this, other)
    }

    fun crossedOver(n: Number): Rule {
        return crossedOver(createConstant(n))
    }

    fun crossedUnder(other: Indicator<Num>): Rule {
        return CrossedDownIndicatorRule(this, other)
    }

    fun crossedUnder(n: Number): Rule {
        return crossedUnder(createConstant(n))
    }

    fun isGreaterThan(other: Indicator<Num>): Rule {
        return OverIndicatorRule(this, other)
    }

    fun isGreaterThan(n: Number): Rule {
        return isGreaterThan(createConstant(n))
    }

    fun isLessThan(other: Indicator<Num>): Rule {
        return UnderIndicatorRule(this, other)
    }

    fun isLessThan(n: Number): Rule {
        return isLessThan(createConstant(n))
    }

    private fun createConstant(n: Number): Indicator<Num> {
        return ConstantIndicator(barSeries, numOf(n))
    }

    override fun getValue(index: Int): Num {
        return delegate[index]
    }

    override val barSeries: BarSeries?
        get() = delegate.barSeries

    override fun numOf(number: Number): Num {
        return delegate.numOf(number)
    }

    override fun toString(): String {
        return delegate.toString()
    }

    companion object {
        /**
         * Creates a fluent NumericIndicator wrapped around a "regular" indicator.
         *
         * @param delegate an indicator
         *
         * @return a fluent NumericIndicator wrapped around the argument
         */
        @JvmStatic
        fun of(delegate: Indicator<Num>): NumericIndicator {
            return NumericIndicator(delegate)
        }

        /**
         * Creates a fluent version of the ClosePriceIndicator
         *
         * @return a NumericIndicator wrapped around a ClosePriceIndicator
         */
        @JvmStatic
        fun closePrice(bs: BarSeries?): NumericIndicator {
            return of(ClosePriceIndicator(bs))
        }

        /**
         * Creates a fluent version of the VolumeIndicator
         *
         * @return a NumericIndicator wrapped around a VolumeIndicator
         */
        @JvmStatic
        fun volume(bs: BarSeries?): NumericIndicator {
            return of(VolumeIndicator(bs))
        }
    }
}