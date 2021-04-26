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
package org.ta4j.core.num;

import java.util.function.Function;

/**
 * Representation of an undefined or unrepresentable value: NaN (not a number)
 * <br>
 * Special behavior in methods such as:
 * <ul>
 * <li>{@link NaN#plus(Num)} => NaN</li>
 * <li>{@link NaN#isEqual(Num)} => true</li>
 * <li>{@link NaN#isPositive()} => false</li>
 * <li>{@link NaN#isNegativeOrZero()} => false</li>
 * <li>{@link NaN#min(Num)} => NaN</li>
 * <li>{@link NaN#max(Num)} => NaN</li>
 * <li>{@link NaN#doubleValue()} => {@link Double#NaN}</li>
 * <li>{@link NaN#intValue()} => throws
 * {@link UnsupportedOperationException}</li>
 * </ul>
 */
public class NaN implements Num {

    /** static Not-a-Number instance */
    public static final Num NaN = new NaN();

    private NaN() {
    }

    /**
     * Returns a {@code Num} version of the given {@code Number}. Warning: This
     * method turns the number into NaN.
     *
     * @param val the number
     * @return {@link #NaN}
     */
    public static Num valueOf(Number val) {
        return NaN;
    }

    @Override
    public int compareTo(Num o) {
        return 0;
    }

    @Override
    public int intValue() {
        throw new UnsupportedOperationException("No NaN represantation for int");
    }

    @Override
    public long longValue() {
        throw new UnsupportedOperationException("No NaN represantation for long");
    }

    @Override
    public float floatValue() {
        return Float.NaN;
    }

    @Override
    public double doubleValue() {
        return Double.NaN;
    }

    @Override
    public Number getDelegate() {
        return null;
    }

    @Override
    public String getName() {
        return toString();
    }

    @Override
    public String toString() {
        return "NaN";
    }

    @Override
    public Num plus(Num augend) {
        return this;
    }

    @Override
    public Num minus(Num subtrahend) {
        return this;
    }

    @Override
    public Num multipliedBy(Num multiplicand) {
        return this;
    }

    @Override
    public Num dividedBy(Num divisor) {
        return this;
    }

    @Override
    public Num remainder(Num divisor) {
        return this;
    }

    @Override
    public Num floor() {
        return this;
    }

    @Override
    public Num ceil() {
        return this;
    }

    @Override
    public Num pow(int n) {
        return this;
    }

    @Override
    public Num pow(Num n) {
        return this;
    }

    @Override
    public Num log() {
        return this;
    }

    @Override
    public Num sqrt() {
        return this;
    }

    @Override
    public Num sqrt(int precision) {
        return this;
    }

    @Override
    public Num abs() {
        return this;
    }

    @Override
    public Num negate() {
        return this;
    }

    @Override
    public boolean isZero() {
        return false;
    }

    @Override
    public boolean isPositive() {
        return false;
    }

    @Override
    public boolean isPositiveOrZero() {
        return false;
    }

    @Override
    public boolean isNegative() {
        return false;
    }

    @Override
    public boolean isNegativeOrZero() {
        return false;
    }

    /**
     * NaN.isEqual(NaN) -> true
     * 
     * @param other the other value, not null
     * @return flase if both values are not NaN
     */
    @Override
    public boolean isEqual(Num other) {
        return other != null && other.equals(NaN);
    }

    @Override
    public boolean isGreaterThan(Num other) {
        return false;
    }

    @Override
    public boolean isGreaterThanOrEqual(Num other) {
        return false;
    }

    @Override
    public boolean isLessThan(Num other) {
        return false;
    }

    @Override
    public boolean isLessThanOrEqual(Num other) {
        return false;
    }

    @Override
    public Num min(Num other) {
        return this;
    }

    @Override
    public Num max(Num other) {
        return this;
    }

    @Override
    public Function<Number, Num> function() {
        return number -> NaN;
    }

    @Override
    public boolean isNaN() {
        return true;
    }
}
