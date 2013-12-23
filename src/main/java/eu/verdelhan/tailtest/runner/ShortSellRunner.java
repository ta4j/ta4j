package eu.verdelhan.tailtest.runner;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Runner;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;

public class ShortSellRunner implements Runner {
	private Runner runner;

	public ShortSellRunner(TimeSeriesSlicer slicer, Strategy strategy) {
		this.runner = new HistoryRunner(slicer, strategy);
	}

	public List<Trade> run(int slicePosition) {
		List<Trade> trades = runner.run(slicePosition);
		List<Trade> tradesWithShortSells = new ArrayList<Trade>();

		for (int i = 0; i < trades.size() - 1; i++) {

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
