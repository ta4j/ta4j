/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A position that enters before an analysis window {@code [2, 4]} and is priced
 * by a holding-cost model reading the close of its entry bar, a bar the window
 * does not contain. On its first evaluation the model updates that close in
 * place without publishing the mutation, so only a value comparison of the bar
 * can reveal it.
 */
final class PreWindowCostRace {

    /** First index of the analysis window. */
    static final int WINDOW_BEGIN = 2;
    /** Last index of the analysis window. */
    static final int WINDOW_END = 4;
    private static final int ENTRY_INDEX = 1;

    private final NumFactory numFactory;
    private final BarSeries series;
    private final Num[] entryClose;
    private final AtomicBoolean updateOnNextCost = new AtomicBoolean(true);

    PreWindowCostRace(NumFactory numFactory) {
        this.numFactory = numFactory;
        List<Bar> bars = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d, 140d)
                .build()
                .getBarData();
        Bar entryBar = bars.get(ENTRY_INDEX);
        entryClose = new Num[] { entryBar.getClosePrice() };
        // A custom bar whose close changes in place without publishing the mutation,
        // so the series revision cannot reveal it.
        Bar mutableEntryBar = new BaseBar(entryBar.getTimePeriod(), entryBar.getBeginTime(), entryBar.getEndTime(),
                entryBar.getOpenPrice(), entryBar.getHighPrice(), entryBar.getLowPrice(), entryBar.getClosePrice(),
                entryBar.getVolume(), entryBar.getAmount(), entryBar.getTrades()) {
            @Override
            public Num getClosePrice() {
                return entryClose[0];
            }
        };
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(List.of(bars.get(0), mutableEntryBar, bars.get(2), bars.get(3), bars.get(4)))
                .build();
    }

    BarSeries series() {
        return series;
    }

    /** @return a record windowed to {@code [2, 5]} that holds the position */
    BaseTradingRecord recordWithPosition() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, WINDOW_BEGIN, WINDOW_END + 1,
                new ZeroCostModel(), new EntryCloseCost());
        record.enter(ENTRY_INDEX, numFactory.numOf(110d), numFactory.one());
        record.exit(3, numFactory.numOf(130d), numFactory.one());
        return record;
    }

    /** @return a record with the same window and no positions */
    BaseTradingRecord emptyRecord() {
        return new BaseTradingRecord(TradeType.BUY, WINDOW_BEGIN, WINDOW_END + 1, new ZeroCostModel(),
                new ZeroCostModel());
    }

    /** @return a position priced by the racing holding-cost model */
    Position position() {
        return new Position(Trade.buyAt(ENTRY_INDEX, numFactory.numOf(110d), numFactory.one()),
                Trade.sellAt(3, numFactory.numOf(130d), numFactory.one()), new ZeroCostModel(), new EntryCloseCost());
    }

    /** @return the entry close after every model evaluation has settled */
    Num entryClose() {
        return series.getBar(ENTRY_INDEX).getClosePrice();
    }

    private final class EntryCloseCost implements CostModel {

        @Override
        public Num calculate(Position position, int finalIndex) {
            Num cost = series.getBar(ENTRY_INDEX).getClosePrice().multipliedBy(numFactory.numOf(0.1d));
            if (updateOnNextCost.compareAndSet(true, false)) {
                entryClose[0] = numFactory.numOf(150d);
            }
            return cost;
        }

        @Override
        public Num calculate(Position position) {
            return calculate(position, position.getExit().getIndex());
        }

        @Override
        public Num calculate(Num price, Num amount) {
            return numFactory.zero();
        }

        @Override
        public boolean equals(CostModel otherModel) {
            return otherModel == this;
        }
    }
}
