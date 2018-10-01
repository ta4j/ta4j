package org.ta4j.core;

import java.time.ZonedDateTime;

public class BarCriteria {

    private final String instrument;
    private final TradeInterval tradeInterval;
    private final ZonedDateTime start;
    private final ZonedDateTime end;

    public BarCriteria(String instrument, TradeInterval tradeInterval, ZonedDateTime start, ZonedDateTime end) {
        this.instrument = instrument;
        this.tradeInterval = tradeInterval;
        this.start = start;
        this.end = end;
    }

    public String getInstrument() {
        return instrument;
    }

    public TradeInterval getTradeInterval() {
        return tradeInterval;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    public String description() {
        return new StringBuilder().append("Instrument: ").append(getInstrument()).append(" - ").append("Interval: ").append(getTradeInterval().name())
                .append(" - ").append("Period: ").append(getStart().toLocalDateTime()).append(" - ").append(getEnd().toLocalDateTime()).toString();
    }
}
