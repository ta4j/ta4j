package eu.verdelhan.ta4j.runner;

import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Runner;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.List;

public class ShortSellRunner implements Runner {
    private Runner runner;

    public ShortSellRunner(TimeSeriesSlicer slicer, Strategy strategy) {
        runner = new HistoryRunner(slicer, strategy);
    }

    @Override
    public List<Trade> run(int slicePosition) {
        List<Trade> trades = runner.run(slicePosition);
        List<Trade> tradesWithShortSells = new ArrayList<Trade>();

        for (int i = 0; i < (trades.size() - 1); i++) {

            Trade trade = trades.get(i);
            tradesWithShortSells.add(trade);

            Trade nextTrade = trades.get(i + 1);

            Trade shortSell = new Trade(OperationType.SELL);
            shortSell.operate(trade.getExit().getIndex());
            shortSell.operate(nextTrade.getEntry().getIndex());
            tradesWithShortSells.add(shortSell);
        }

        if (!trades.isEmpty()) {
            tradesWithShortSells.add(trades.get(trades.size() - 1));
        }

        return tradesWithShortSells;
    }
}
