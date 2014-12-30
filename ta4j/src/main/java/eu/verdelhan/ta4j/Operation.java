/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j;

/**
 * An operation of a {@link Trade trade}.
 * <p>
 * The operation is defined by:
 * <ul>
 * <li>the index (in the {@link TimeSeries time series}) it is executed
 * <li>a {@link OperationType type} (BUY or SELL)
 * </ul>
 */
public class Operation {

    /** Type of the operation */
    private OperationType type;

    /** The index the operation was executed */
    private int index;

    /**
     * @param index the index the operation was executed
     * @param type the type of the operation
     */
    public Operation(int index, OperationType type) {
        this.type = type;
        this.index = index;
    }

    /**
     * @return the type of the operation (BUY or SELL)
     */
    public OperationType getType() {
        return type;
    }

    /**
     * @return true if this is a BUY operation, false otherwise
     */
    public boolean isBuy() {
        return type == OperationType.BUY;
    }

    /**
     * @return true if this is a SELL operation, false otherwise
     */
    public boolean isSell() {
        return type == OperationType.SELL;
    }

    /**
     * @return the index the operation was executed
     */
    public int getIndex() {
        return index;
    }

    @Override
    public int hashCode() {
        return index + (type.hashCode() * 31);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Operation) {
            Operation o = (Operation) obj;
            return type.equals(o.getType()) && (index == o.getIndex());
        }
        return false;
    }

    @Override
    public String toString() {
        return " Index: " + index + " type: " + type.toString();
    }

}
