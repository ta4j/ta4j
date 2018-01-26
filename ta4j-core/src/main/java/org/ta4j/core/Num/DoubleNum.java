/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.Num;

import java.util.function.Function;

import static org.ta4j.core.Num.NaN.NaN;

/**
 * Representation of Double. High performance, lower precision.
 * @apiNote the delegate should never become a NaN value. No self NaN checks provided
 */
public class DoubleNum implements Num {

    private static final long serialVersionUID = -2611177221813615070L;

    private final double delegate;

    private final static double EPS = 0.00001; // precision

    @Override
    public Function<Number, Num> function() {
        return DoubleNum::valueOf;
    }

    public DoubleNum(double val){
        delegate = val;
    }


    @Override
    public Double getDelegate() {
        return delegate;
    }

    @Override
    public String getName() {
        return "DoubleNum";
    }

    @Override
    public Num plus(Num augend) {
        if (augend == NaN){
            return NaN;
        }
        if (!(augend instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format("The underlying data types '%s' and '%s' are not the same",this.getClass(), augend.getClass()));
        }

        return new DoubleNum(delegate+((DoubleNum) augend).delegate);
    }

    @Override
    public Num minus(Num subtrahend) {
        if (subtrahend == NaN){
            return NaN;
        }
        if (!(subtrahend instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format(
                    "The underlying data types '%s' and '%s' are not the same"
                    ,this.getClass(), subtrahend.getClass()));
        }

        return new DoubleNum(delegate-((DoubleNum) subtrahend).delegate);
    }

    @Override
    public Num multipliedBy(Num multiplicand) {
        if (multiplicand == NaN){
            return NaN;
        }
        if (!(multiplicand instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format(
                    "The underlying data types '%s' and '%s' are not the same"
                    ,this.getClass(), multiplicand.getClass()));
        }

        return new DoubleNum(delegate*((DoubleNum) multiplicand).delegate);
    }

    @Override
    public Num dividedBy(Num divisor) {
        if (divisor == NaN){
            return NaN;
        }
        if (!(divisor instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format(
                    "The underlying data types '%s' and '%s' are not the same"
                    ,this.getClass(), divisor.getClass()));
        }
        DoubleNum divisorD = (DoubleNum) divisor;
        if(((DoubleNum) divisor).delegate==0){
            return NaN;
        }
        return new DoubleNum(delegate/divisorD.delegate);
    }

    @Override
    public Num remainder(Num divisor) {
        if (divisor == NaN){
            return NaN;
        }
        if (!(divisor instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format(
                    "The underlying data types '%s' and '%s' are not the same"
                    ,this.getClass(), divisor.getClass()));
        }

        return new DoubleNum(delegate%((DoubleNum) divisor).delegate);
    }

    @Override
    public Num pow(int n) {
        return new DoubleNum(Math.pow(delegate,n));
    }

    @Override
    public Num abs() {
        return new DoubleNum(Math.abs(delegate));
    }

    @Override
    public boolean isZero() {
        return delegate == 0;
    }

    @Override
    public boolean isPositive() {
        return delegate > 0;
    }

    @Override
    public boolean isPositiveOrZero() {
        return delegate >= 0;
    }

    @Override
    public boolean isNegative() {
        return delegate < 0;
    }

    @Override
    public boolean isNegativeOrZero() {
        return delegate <= 0;
    }

    @Override
    public boolean isEqual(Num other) {
        if (other == NaN){
            return true;
        }
        if (!(other instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format(
                    "The underlying data types '%s' and '%s' are not the same"
                    ,this.getClass(), other.getClass()));
        }
        return delegate == ((DoubleNum) other).delegate;
    }

    /**
     * Checks if this value is greater than another.
     * @param other the other value, not null
     * @return true is this is greater than the specified value, false otherwise
     */
    public boolean isGreaterThan(Num other) {
        return (other != NaN) && compareTo(other) > 0;
    }

    /**
     * Checks if this value is greater than or equal to another.
     * @param other the other value, not null
     * @return true is this is greater than or equal to the specified value, false otherwise
     */
    public boolean isGreaterThanOrEqual(Num other) {
        return (other != NaN) && compareTo(other) > -1;
    }

    /**
     * Checks if this value is less than another.
     * @param other the other value, not null
     * @return true is this is less than the specified value, false otherwise
     */
    public boolean isLessThan(Num other) {
        return (other != NaN) && compareTo(other) < 0;
    }

    @Override
    public boolean isLessThanOrEqual(Num other) {
        return (other != NaN) && compareTo(other) < 1;
    }

    /**
     * Checks if this value is less than or equal to another.
     * @param other the other value, not null
     * @return true is this is less than or equal to the specified value, false otherwise
     */
    public boolean isLessThanOrEqual(Number other) {
        return (other != NaN) && compareTo(function().apply(other)) < 1;
    }

    @Override
    public Num min(Num other) {
        if (other == NaN){
            return NaN;
        }
        if (!(other instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format(
                    "The underlying data types '%s' and '%s' are not the same"
                    ,this.getClass(), other.getClass()));
        }
        return new DoubleNum(Math.min(delegate,((DoubleNum) other).delegate));
    }

    @Override
    public Num max(Num other) {
        if (other == NaN){
            return NaN;
        }
        if (!(other instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format(
                    "The underlying data types '%s' and '%s' are not the same"
                    ,this.getClass(), other.getClass()));
        }
        return new DoubleNum(Math.max(delegate,((DoubleNum)other).delegate));
    }

    @Override
    public boolean isNaN() {
        return this.equals(NaN);
    }


    @Override
    public int hashCode() {
        return ((Double) (delegate)).hashCode();
    }

    @Override
    public String toString() {
        return Double.toString(delegate);

    }

    @Override
    public boolean equals(Object obj) {
        if(obj==null){
            return false;
        }
        if (this == NaN && obj == NaN){
            return true;
        }
        if(!(obj instanceof DoubleNum)) {
            return false;
        }
        DoubleNum doubleNumObj = (DoubleNum) obj;

        return Math.abs(delegate - doubleNumObj.delegate) < EPS;
    }

    @Override
    public int compareTo(Num o) {
        if (this == NaN || o == NaN){
            return 0;
        }
        if (!(o instanceof DoubleNum)){
            throw new IllegalArgumentException(String.format(
                    "The underlying data types '%s' and '%s' are not the same"
                    ,this.getClass(), o.getClass()));
        }
        DoubleNum doubleNumO = (DoubleNum)o;
        return Double.compare(delegate, doubleNumO.delegate);
    }

    public static Num valueOf(int i) {
        return new DoubleNum((double)i);
    }

    public static Num valueOf(long i) {
        return new DoubleNum((double)i);
    }

    public static Num valueOf(short i) {
        return new DoubleNum((double)i);
    }

    public static Num valueOf(float i) {
        return new DoubleNum((double)i);
    }

    public static Num valueOf(String i) {
        return new DoubleNum(Double.parseDouble(i));
    }

    public static Num valueOf(Number i) {
        return new DoubleNum(Double.parseDouble(i.toString()));
    }
}
