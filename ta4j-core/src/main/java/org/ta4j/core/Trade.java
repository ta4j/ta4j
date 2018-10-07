package org.ta4j.core;

import org.ta4j.core.Order.OrderType;
import org.ta4j.core.num.Num;

import java.io.Serializable;
import java.util.Objects;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Pair of two {@link Order orders}.
 * </p>
 * The exit order has the complement type of the entry order.<br>
 * I.e.:
 *   entry == BUY --> exit == SELL
 *   entry == SELL --> exit == BUY
 */
public class Trade implements Serializable {

	private static final long serialVersionUID = -5484709075767220358L;

	/** The entry order */
    private Order entry;

    /** The exit order */
    private Order exit;

    /** The type of the entry order */
    private OrderType startingType;

    /**
     * Constructor.
     */
    public Trade() {
        this(OrderType.BUY);
    }

    /**
     * Constructor.
     * @param startingType the starting {@link OrderType order type} of the trade (i.e. type of the entry order)
     */
    public Trade(OrderType startingType) {
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = startingType;
    }

    /**
     * Constructor.
     * @param entry the entry {@link Order order}
     * @param exit the exit {@link Order order}
     */
    public Trade(Order entry, Order exit) {
        if (entry.getType().equals(exit.getType())) {
            throw new IllegalArgumentException("Both orders must have different types");
        }
        this.startingType = entry.getType();
        this.entry = entry;
        this.exit = exit;
    }

    /**
     * @return the entry {@link Order order} of the trade
     */
    public Order getEntry() {
        return entry;
    }

    /**
     * @return the exit {@link Order order} of the trade
     */
    public Order getExit() {
        return exit;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trade) {
            Trade t = (Trade) obj;
            return (entry == null ? t.getEntry() == null : entry.equals(t.getEntry()))
            		&& (exit == null ? t.getExit() == null : exit.equals(t.getExit()));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entry, exit);
    }

    /**
     * Operates the trade at the index-th position
     * @param index the bar index
     * @return the order
     */
    public Order operate(int index) {
        return operate(index, NaN, NaN);
    }

    /**
     * Operates the trade at the index-th position
     * @param index the bar index
     * @param price the price
     * @param amount the amount
     * @return the order
     */
    public Order operate(int index, Num price, Num amount) {
        Order order = null;
        if (isNew()) {
            order = new Order(index, startingType, price, amount);
            entry = order;
        } else if (isOpened()) {
            if (index < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryOrder index");
            }
            order = new Order(index, startingType.complementType(), price, amount);
            exit = order;
        }
        return order;
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

    /**
     * @return the profit or loss of the trade
     */
    public Num getProfit() {
        return exit.getValue().minus(entry.getValue());
    }
}
