/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.numeric;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
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
 *
 * <p>
 * Methods like plus(), minus() and sqrt() correspond directly to methods in the
 * {@code Num} interface. These methods create "lightweight" (not cached)
 * indicators to add, subtract, etc. Many methods are overloaded to accept
 * either {@code Indicator<Num>} or {@code Number} arguments.
 *
 * <p>
 * Methods like sma() and ema() simply create the corresponding indicator
 * objects, (SMAIndicator or EMAIndicator, for example) with "this" as the first
 * argument. These methods usually instantiate cached objects.
 *
 * <p>
 * Another set of methods, like crossedOver() and isGreaterThan() create Rule
 * objects. These are also overloaded to accept both {@code Indicator<Num>} and
 * {@code Number} arguments.
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
     * Creates a fluent version of the ClosePriceIndicator.
     *
     * @return a NumericIndicator wrapped around a ClosePriceIndicator
     */
    public static NumericIndicator closePrice(BarSeries bs) {
        return of(new ClosePriceIndicator(bs));
    }

    /**
     * Creates a fluent version of the VolumeIndicator.
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

    /**
     * @param other the other indicator
     * @return {@code this + other}, rounded as necessary
     */
    public NumericIndicator plus(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperationIndicator.sum(this, other));
    }

    /**
     * @param n the other number
     * @return {@code this + n}, rounded as necessary
     */
    public NumericIndicator plus(Number n) {
        return plus(createConstant(n));
    }

    /**
     * @param other the other indicator
     * @return {@code this - other}, rounded as necessary
     */
    public NumericIndicator minus(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperationIndicator.difference(this, other));
    }

    /**
     * @param n the other number
     * @return {@code this - n}, rounded as necessary
     */
    public NumericIndicator minus(Number n) {
        return minus(createConstant(n));
    }

    /**
     * @param other the other indicator
     * @return {@code this * other}, rounded as necessary
     */
    public NumericIndicator multipliedBy(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperationIndicator.product(this, other));
    }

    /**
     * @param n the other number
     * @return {@code this * n}, rounded as necessary
     */
    public NumericIndicator multipliedBy(Number n) {
        return multipliedBy(createConstant(n));
    }

    /**
     * @param other the other indicator
     * @return {@code this / other}, rounded as necessary
     */
    public NumericIndicator dividedBy(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperationIndicator.quotient(this, other));
    }

    /**
     * @param n the other number
     * @return {@code this / n}, rounded as necessary
     */
    public NumericIndicator dividedBy(Number n) {
        return dividedBy(createConstant(n));
    }

    /**
     * @param other the other indicator
     * @return the smaller of {@code this} and {@code other}; if they are equal,
     *         {@code this} is returned.
     */
    public NumericIndicator min(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperationIndicator.min(this, other));
    }

    /**
     * @param n the other number
     * @return the smaller of {@code this} and {@code n}; if they are equal,
     *         {@code this} is returned.
     */
    public NumericIndicator min(Number n) {
        return min(createConstant(n));
    }

    /**
     * @param other the other indicator
     * @return the greater of {@code this} and {@code other}; if they are equal,
     *         {@code this} is returned.
     */
    public NumericIndicator max(Indicator<Num> other) {
        return NumericIndicator.of(BinaryOperationIndicator.max(this, other));
    }

    /**
     * @param n the other number
     * @return the greater of {@code this} and {@code n}; if they are equal,
     *         {@code this} is returned.
     */
    public NumericIndicator max(Number n) {
        return max(createConstant(n));
    }

    /**
     * Returns an Indicator whose values are the absolute values of {@code this}.
     *
     * @return {@code abs(this)}
     */
    public NumericIndicator abs() {
        return NumericIndicator.of(UnaryOperationIndicator.abs(this));
    }

    /**
     * Returns an Indicator whose values are √(this).
     *
     * @return {@code √(this)}
     */
    public NumericIndicator sqrt() {
        return NumericIndicator.of(UnaryOperationIndicator.sqrt(this));
    }

    /**
     * Returns an Indicator whose values are {@code this * this}.
     *
     * @return {@code this * this}
     */
    public NumericIndicator squared() {
        // TODO: implement pow(n); a few others
        return this.multipliedBy(this);
    }

    /**
     * @param barCount the time frame
     * @return the {@link SMAIndicator} of {@code this}
     */
    public NumericIndicator sma(int barCount) {
        return NumericIndicator.of(new SMAIndicator(this, barCount));
    }

    /**
     * @param barCount the time frame
     * @return the {@link EMAIndicator} of {@code this}
     */
    public NumericIndicator ema(int barCount) {
        return NumericIndicator.of(new EMAIndicator(this, barCount));
    }

    /**
     * @param barCount the time frame
     * @return the {@link StandardDeviationIndicator} of {@code this}
     */
    public NumericIndicator stddev(int barCount) {
        return NumericIndicator.of(new StandardDeviationIndicator(this, barCount));
    }

    /**
     * @param barCount the time frame
     * @return the {@link HighestValueIndicator} of {@code this}
     */
    public NumericIndicator highest(int barCount) {
        return NumericIndicator.of(new HighestValueIndicator(this, barCount));
    }

    /**
     * @param barCount the time frame
     * @return the {@link LowestValueIndicator} of {@code this}
     */
    public NumericIndicator lowest(int barCount) {
        return NumericIndicator.of(new LowestValueIndicator(this, barCount));
    }

    /**
     * @param barCount the time frame
     * @return the {@link PreviousValueIndicator} of {@code this}
     */
    public NumericIndicator previous(int barCount) {
        return NumericIndicator.of(new PreviousValueIndicator(this, barCount));
    }

    /**
     * @return the {@link PreviousValueIndicator} of {@code this} with
     *         {@code barCount=1}
     */
    public NumericIndicator previous() {
        return previous(1);
    }

    /**
     * @param other the other indicator
     * @return the {@link CrossedUpIndicatorRule} of {@code this} and {@code other}
     */
    public Rule crossedOver(Indicator<Num> other) {
        return new CrossedUpIndicatorRule(this, other);
    }

    /**
     * @param n the other number
     * @return the {@link CrossedUpIndicatorRule} of {@code this} and {@code n}
     */
    public Rule crossedOver(Number n) {
        return crossedOver(createConstant(n));
    }

    /**
     * @param other the other indicator
     * @return the {@link CrossedDownIndicatorRule} of {@code this} and
     *         {@code other}
     */
    public Rule crossedUnder(Indicator<Num> other) {
        return new CrossedDownIndicatorRule(this, other);
    }

    /**
     * @param n the other number
     * @return the {@link CrossedDownIndicatorRule} of {@code this} and {@code n}
     */
    public Rule crossedUnder(Number n) {
        return crossedUnder(createConstant(n));
    }

    /**
     * @param other the other indicator
     * @return the {@link OverIndicatorRule} of {@code this} and {@code other}
     */
    public Rule isGreaterThan(Indicator<Num> other) {
        return new OverIndicatorRule(this, other);
    }

    /**
     * @param n the other number
     * @return the {@link OverIndicatorRule} of {@code this} and {@code n}
     */
    public Rule isGreaterThan(Number n) {
        return isGreaterThan(createConstant(n));
    }

    /**
     * @param other the other indicator
     * @return the {@link UnderIndicatorRule} of {@code this} and {@code other}
     */
    public Rule isLessThan(Indicator<Num> other) {
        return new UnderIndicatorRule(this, other);
    }

    /**
     * @param n the other number
     * @return the {@link UnderIndicatorRule} of {@code this} and {@code n}
     */
    public Rule isLessThan(Number n) {
        return isLessThan(createConstant(n));
    }

    private Indicator<Num> createConstant(Number n) {
        return new ConstantIndicator<>(getBarSeries(), getBarSeries().numFactory().numOf(n));
    }

    @Override
    public Num getValue(int index) {
        return delegate.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return delegate.getCountOfUnstableBars();
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
