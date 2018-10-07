package org.ta4j.core.analysis;

import org.ta4j.core.*;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cash flow.
 * </p>
 * This class allows to follow the money cash flow involved by a list of trades over a time series.
 */
public class CashFlow implements Indicator<Num> {

    /** The time series */
    private final TimeSeries timeSeries;

    /** The cash flow values */
    private List<Num> values;

    /**
     * Constructor.
     * @param timeSeries the time series
     * @param trade a single trade
     */
    public CashFlow(TimeSeries timeSeries, Trade trade) {
        this.timeSeries = timeSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(trade);
        fillToTheEnd();
    }

    /**
     * Constructor.
     * @param timeSeries the time series
     * @param tradingRecord the trading record
     */
    public CashFlow(TimeSeries timeSeries, TradingRecord tradingRecord) {
        this.timeSeries = timeSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(tradingRecord);

        fillToTheEnd();
    }

    /**
     * @param index the bar index
     * @return the cash flow value at the index-th position
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    @Override
    public TimeSeries getTimeSeries() {
        return timeSeries;
    }

    @Override
    public Num numOf(Number number) {
        return timeSeries.numOf(number);
    }

    /**
     * @return the size of the time series
     */
    public int getSize() {
        return timeSeries.getBarCount();
    }

    /**
     * Calculates the cash flow for a single trade.
     * @param trade a single trade
     */
    private void calculate(Trade trade) {
        final int entryIndex = trade.getEntry().getIndex();
        int begin = entryIndex + 1;
        if (begin > values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(begin - values.size(), lastValue));
        }
        int end = trade.getExit().getIndex();
        for (int i = Math.max(begin, 1); i <= end; i++) {
            Num ratio;
            if (trade.getEntry().isBuy()) {
                ratio = getRatio(entryIndex, i);
            } else {
                ratio = getRatio(i, entryIndex);
            }
            values.add(values.get(entryIndex).multipliedBy(ratio));
        }
    }

    private Num getRatio(int entryIndex, int exitIndex) {
        return timeSeries.getBar(exitIndex).getClosePrice().dividedBy(timeSeries.getBar(entryIndex).getClosePrice());
    }

    /**
     * Calculates the cash flow for a trading record.
     * @param tradingRecord the trading record
     */
    private void calculate(TradingRecord tradingRecord) {
        for (Trade trade : tradingRecord.getTrades()) {
            // For each trade...
            calculate(trade);
        }
    }

    /**
     * Fills with last value till the end of the series.
     */
    private void fillToTheEnd() {
        if (timeSeries.getEndIndex() >= values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(timeSeries.getEndIndex() - values.size() + 1, lastValue));
        }
    }
}