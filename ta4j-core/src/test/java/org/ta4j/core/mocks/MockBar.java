package org.ta4j.core.mocks;

import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.function.Function;


/**
 * A mock bar with sample data.
 */
public class MockBar extends BaseBar {

    private int trades = 0;

    public MockBar(double closePrice, Function<Number, Num> numFunction) {
        this(ZonedDateTime.now(), closePrice, numFunction);
    }

    public MockBar(double closePrice, double volume, Function<Number, Num> numFunction) {
        super(ZonedDateTime.now(), 0, 0, 0, closePrice, volume, numFunction);
    }

    public MockBar(ZonedDateTime endTime, double closePrice, Function<Number, Num> numFunction) {
        super(endTime, 0, 0, 0, closePrice, 0, numFunction);
    }

    public MockBar(ZonedDateTime endTime, double closePrice, double volume, Function<Number, Num> numFunction) {
        super(endTime, 0, 0, 0, closePrice, volume, numFunction);
    }

    public MockBar(double openPrice, double closePrice, double maxPrice, double minPrice, Function<Number, Num> numFunction) {
        super(ZonedDateTime.now(), openPrice, maxPrice, minPrice, closePrice, 1,numFunction);
    }

    public MockBar(double openPrice, double closePrice, double maxPrice, double minPrice, double volume, Function<Number, Num> numFunction) {
        super(ZonedDateTime.now(), openPrice, maxPrice, minPrice, closePrice, volume,numFunction);
    }

    public MockBar(ZonedDateTime endTime, double openPrice, double closePrice, double maxPrice, double minPrice, double amount, double volume, int trades, Function<Number, Num> numFunction) {
        super(endTime, openPrice, maxPrice, minPrice, closePrice, volume,numFunction);
        this.trades = trades;
    }

    @Override
    public int getTrades() {
        return trades;
    }
}
