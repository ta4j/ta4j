/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
 * Set of two {@link Operation operations}. Not a single operation.
 * 
 */
public class Trade {

    private Operation entry;

    private Operation exit;

    private OperationType startingType;

    /**
     * Constructor.
     */
    public Trade() {
        this(OperationType.BUY);
    }

    /**
     * Constructor.
     * @param startingType the starting {@link OperationType operation type} of the trade
     */
    public Trade(OperationType startingType) {
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = startingType;
    }

    /**
     * Constructor.
     * @param entry the entry {@link Operation operation}
     * @param exit the exit {@link Operation operation}
     */
    public Trade(Operation entry, Operation exit) {
        this.entry = entry;
        this.exit = exit;
    }

    /**
     * @return the entry {@link Operation operation} of the trade
     */
    public Operation getEntry() {
        return entry;
    }

    /**
     * @return the exit {@link Operation operation} of the trade
     */
    public Operation getExit() {
        return exit;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trade) {
            Trade t = (Trade) obj;
            return entry.equals(t.getEntry()) && exit.equals(t.getExit());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (entry.hashCode() * 31) + (exit.hashCode() * 17);
    }

    /**
     * Operates the trade at the i-th position
     * @param i the index
     */
    public void operate(int i) {
        if (isNew()) {
            entry = new Operation(i, startingType);
        } else if (isOpened()) {
            if (i < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryOperation index");
            }
            exit = new Operation(i, startingType.complementType());
        }
    }

    /**
     * @return true if the trade is closed, false otherwise
     */
    public boolean isClosed() {
        return (entry != null) && (exit != null);
    }

    /**
     * @return true if the trade is opened, false otherwise
     */
    public boolean isOpened() {
        return (entry != null) && (exit == null);
    }

    /**
     * @return true if the trade is new, false otherwise
     */
    public boolean isNew() {
        return (entry == null) && (exit == null);
    }

    @Override
    public String toString() {
        return "Entry: " + entry + " exit: " + exit;
    }
}
