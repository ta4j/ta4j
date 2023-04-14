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
package org.ta4j.core.indicators.numeric;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

/**
 * NumericIndicator is a "fluent decorator" for Indicator<Num>. It provides
 * methods to create rules and other "lightweight" indicators, using a
 * (hopefully) natural-looking and expressive series of method calls.
 * <p>
 * Methods like plus(), minus() and sqrt() correspond directly to methods in the
 * Num interface. These methods create "lightweight" (not cached) indicators to
 * add, subtract, etc. Many methods are overloaded to accept either
 * Indicator<Num> or Number arguments.
 * <p>
 * Methods like sma() and ema() simply create the corresponding indicator
 * objects, (SMAIndicator or EMAIndicator, for example) with "this" as the first
 * argument. These methods usually instantiate cached objects.
 * <p>
 * Another set of methods, like crossedOver() and isGreaterThan() create Rule
 * objects. These are also overloaded to accept both Indicator<Num> and Number
 * arguments.
 */
public class NumericIndicator implements Indicator<Num> {

    /**
     * Creates a fluent NumericIndicator wrapped around a "regular" indicator.
     * 
     * @param delegate an indicator
     * 
     * @return a fluent NumericIndicator wrapped around the argument
     */
    public static NumericIndicator of(Indicator<Num> delegate) {
        return new NumericIndicator(delegate);
    }

    /**
     * Creates a fluent version of the ClosePriceIndicator
     * 
     * @return a NumericIndicator wrapped around a ClosePriceIndicator
     */
    public static NumericIndicator closePrice(BarSeries bs) {
        return of(new ClosePriceIndicator(bs));
    }

    /**
     * Creates a fluent version of the VolumeIndicator
     * 
     * @return a NumericIndicator wrapped around a VolumeIndicator
     */
    public static NumericIndicator volume(BarSeries bs) {
        return of(new VolumeIndicator(bs));
    }

    protected final Indicator<Num> delegate;

    protected NumericIndicator(Indicator<Num> delegate) {
        this.delegate = delegate;
    }

    public Indicator<Num> delegate() {
        return delegate;
    }

    public NumericIndicator plus(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperation.sum(this, other));
    }

    public NumericIndicator plus(Number n) {
        return plus(createConstant(n));
    }

    public NumericIndicator minus(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperation.difference(this, other));
    }

    public NumericIndicator minus(Number n) {
        return minus(createConstant(n));
    }

    public NumericIndicator multipliedBy(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperation.product(this, other));
    }

    public NumericIndicator multipliedBy(Number n) {
        return multipliedBy(createConstant(n));
    }

    public NumericIndicator dividedBy(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperation.quotient(this, other));
    }

    public NumericIndicator dividedBy(Number n) {
        return dividedBy(createConstant(n));
    }

    public NumericIndicator min(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperation.min(this, other));
    }

    public NumericIndicator min(Number n) {
        return min(createConstant(n));
    }

    public NumericIndicator max(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperation.max(this, other));
    }

    public NumericIndicator max(Number n) {
        return max(createConstant(n));
    }

    public NumericIndicator abs() {
        return NumericIndicator.of(UnaryOperation.abs(this));
    }

    public NumericIndicator sqrt() {
        return NumericIndicator.of(UnaryOperation.sqrt(this));
    }

    public NumericIndicator squared() {
        // TODO: implement pow(n); a few others
        return this.multipliedBy(this);
    }

    public NumericIndicator sma(int n) {
        return NumericIndicator.of(new SMAIndicator(this, n));
    }

    public NumericIndicator ema(int n) {
        return NumericIndicator.of(new EMAIndicator(this, n));
    }

    public NumericIndicator stddev(int n) {
        return NumericIndicator.of(new StandardDeviationIndicator(this, n));
    }

    public NumericIndicator highest(int n) {
        return NumericIndicator.of(new HighestValueIndicator(this, n));
    }

    public NumericIndicator lowest(int n) {
        return NumericIndicator.of(new LowestValueIndicator(this, n));
    }

    public NumericIndicator previous(int n) {
        return NumericIndicator.of(new PreviousValueIndicator(this, n));
    }

    public Indicator<Num> previous() {
        return previous(1);
    }

    public Rule crossedOver(Indicator<Num> other) {
        return new CrossedUpIndicatorRule(this, other);
    }

    public Rule crossedOver(Number n) {
        return crossedOver(createConstant(n));
    }

    public Rule crossedUnder(Indicator<Num> other) {
        return new CrossedDownIndicatorRule(this, other);
    }

    public Rule crossedUnder(Number n) {
        return crossedUnder(createConstant(n));
    }

    public Rule isGreaterThan(Indicator<Num> other) {
        return new OverIndicatorRule(this, other);
    }

    public Rule isGreaterThan(Number n) {
        return isGreaterThan(createConstant(n));
    }

    public Rule isLessThan(Indicator<Num> other) {
        return new UnderIndicatorRule(this, other);
    }

    public Rule isLessThan(Number n) {
        return isLessThan(createConstant(n));
    }

    private Indicator<Num> createConstant(Number n) {
        return new ConstantIndicator<>(getBarSeries(), numOf(n));
    }

    @Override
    public Num getValue(int index) {
        return delegate.getValue(index);
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }

    @Override
    public BarSeries getBarSeries() {
        return delegate.getBarSeries();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
