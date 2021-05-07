package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

public abstract class PositionBasedIndicator extends CachedIndicator<Num> {

    private final TradingRecord tradingRecord;

    public PositionBasedIndicator(BarSeries series, TradingRecord tradingRecord) {
        super(series);
        this.tradingRecord = tradingRecord;
    }

    @Override
    protected Num calculate(int index) {
        return NaN;
    }

    abstract Num calculateLastPositionWasEntry(Position entryPosition, int index);

    abstract Num calculateLastPositionWasExit(Position exitPosition, int index);
}
