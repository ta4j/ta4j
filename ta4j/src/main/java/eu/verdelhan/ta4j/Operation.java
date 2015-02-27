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

    /**
     * The type of an {@link Operation operation}.
     * <p>
     * A BUY operation correspond to a <i>BID</i> order.<p>
     * A SELL operation correspond to an <i>ASK</i> order.
     */
    public enum OperationType {

        BUY {
            @Override
            public OperationType complementType() {
                return SELL;
            }
        },
        SELL {
            @Override
            public OperationType complementType() {
                return BUY;
            }
        };

        /**
         * @return the complementary operation type
         */
        public abstract OperationType complementType();
    }
    
    /** Type of the operation */
    private OperationType type;

    /** The index the operation was executed */
    private int index;

    /** The price for the operation */
    private Decimal price = Decimal.NaN;
    
    /** The amount to be (or that was) ordered in the operation */
    private Decimal amount = Decimal.NaN;
    
    /**
     * Constructor.
     * @param index the index the operation is executed
     * @param type the type of the operation
     */
    protected Operation(int index, OperationType type) {
        this.type = type;
        this.index = index;
    }

    /**
     * Constructor.
     * @param index the index the operation is executed
     * @param type the type of the operation
     * @param price the price for the operation
     * @param amount the amount to be (or that was) ordered in the operation
     */
    protected Operation(int index, OperationType type, Decimal price, Decimal amount) {
        this(index, type);
        this.price = price;
        this.amount = amount;
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
     * @return the index the operation is executed
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return the price for the operation
     */
    public Decimal getPrice() {
        return price;
    }

    /**
     * @return the amount to be (or that was) ordered in the operation
     */
    public Decimal getAmount() {
        return amount;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (this.type != null ? this.type.hashCode() : 0);
        hash = 29 * hash + this.index;
        hash = 29 * hash + (this.price != null ? this.price.hashCode() : 0);
        hash = 29 * hash + (this.amount != null ? this.amount.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Operation other = (Operation) obj;
        if (this.type != other.type) {
            return false;
        }
        if (this.index != other.index) {
            return false;
        }
        if (this.price != other.price && (this.price == null || !this.price.equals(other.price))) {
            return false;
        }
        if (this.amount != other.amount && (this.amount == null || !this.amount.equals(other.amount))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Operation{" + "type=" + type + ", index=" + index + ", price=" + price + ", amount=" + amount + '}';
    }
    
    /**
     * @param index the index the operation is executed
     * @return a BUY operation
     */
    public static Operation buyAt(int index) {
        return new Operation(index, OperationType.BUY);
    }

    /**
     * @param index the index the operation is executed
     * @param price the price for the operation
     * @param amount the amount to be (or that was) bought
     * @return a BUY operation
     */
    public static Operation buyAt(int index, Decimal price, Decimal amount) {
        return new Operation(index, OperationType.BUY, price, amount);
    }

    /**
     * @param index the index the operation is executed
     * @return a SELL operation
     */
    public static Operation sellAt(int index) {
        return new Operation(index, OperationType.SELL);
    }

    /**
     * @param index the index the operation is executed
     * @param price the price for the operation
     * @param amount the amount to be (or that was) sold
     * @return a SELL operation
     */
    public static Operation sellAt(int index, Decimal price, Decimal amount) {
        return new Operation(index, OperationType.SELL, price, amount);
    }
}
