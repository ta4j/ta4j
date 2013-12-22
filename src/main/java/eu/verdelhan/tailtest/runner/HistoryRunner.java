package net.sf.tail.runner;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.OperationType;
import net.sf.tail.Runner;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Trade;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class HistoryRunner implements Runner {

	private OperationType operationType;

	private TimeSeriesSlicer slicer;

	private Strategy strategy;

	private ArrayList<List<Trade>> listTradesResults;

	private static final Logger LOG = Logger.getLogger(HistoryRunner.class);

	public HistoryRunner(OperationType type,TimeSeriesSlicer slicer,Strategy strategy) {
		if (type == null || slicer == null || strategy == null)
			throw new NullPointerException();
		this.slicer = slicer;
		this.strategy = strategy;
		this.operationType = type;
		LOG.setLevel(Level.WARN);
		listTradesResults = new ArrayList<List<Trade>>();
	}
	public HistoryRunner(TimeSeriesSlicer slicer,Strategy strategy)
	{
		this(OperationType.BUY,slicer,strategy);
	}

	public List<Trade> run(int slicePosition) {
		if(listTradesResults.size() < slicePosition){
			listTradesResults.add(run(slicePosition - 1));
		}
		else if(listTradesResults.size() > slicePosition){
			return listTradesResults.get(slicePosition);
		}


		int begin = 0;
		int end = 0;
		if(listTradesResults.size() == 0){
			begin = slicer.getSlice(slicePosition).getBegin();
			end = slicer.getSlice(slicePosition).getEnd();
		}else{

			end = slicer.getSlice(slicePosition).getEnd();

			int i = listTradesResults.size()-1;
			List<Trade> lastTrades = listTradesResults.get(i);
			while(lastTrades.size() == 0 && i > 0){
				i--;
				lastTrades = listTradesResults.get(i);
			}

			if(i <= 0){
				begin = slicer.getSlice(slicePosition).getBegin();

			}else{
				Trade lastTrade = lastTrades.get(lastTrades.size()-1);
				begin = lastTrade.getExit().getIndex()+1;

				if(begin > end){
					return new ArrayList<Trade>();
				}
			}
		}

		LOG.info("running strategy " + strategy);
		List<Trade> trades = new ArrayList<Trade>();
		Trade lastTrade = new Trade(operationType);
		for (int i = Math.max(begin, 0); i <= end; i++) {
			if (strategy.shouldOperate(lastTrade, i)) {
				lastTrade.operate(i);
				if (lastTrade.isClosed()) {
					trades.add(lastTrade);
					LOG.debug("new trade: " + lastTrade);
					lastTrade = new Trade(operationType);
				}
			}
		}
		if (lastTrade.isOpened()) {
			int j = 1;
			while(slicer.getSlices() > slicePosition + j) {
				int start = Math.max(slicer.getSlice(slicePosition + j).getBegin(), end);
				int last = slicer.getSlice(slicePosition + j).getEnd();

				for (int i = start; i <= last; i++) {
					if (strategy.shouldOperate(lastTrade, i)) {
						lastTrade.operate(i);
						break;
					}
				}
				if(lastTrade.isClosed()){
					trades.add(lastTrade);
					LOG.debug("new trade: " + lastTrade);
					break;
				}
				j++;
			}
	}
	listTradesResults.add(trades);
	return trades;
}
@Override
public int hashCode() {
	final int prime = 31;
	int result = 1;
	result = prime * result + ((operationType == null) ? 0 : operationType.hashCode());
	result = prime * result + ((slicer == null) ? 0 : slicer.hashCode());
	result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
	return result;
}
@Override
public boolean equals(Object obj) {
	if (this == obj)
		return true;
	if (obj == null)
		return false;
	if (getClass() != obj.getClass())
		return false;
	final HistoryRunner other = (HistoryRunner) obj;
	if (operationType == null) {
		if (other.operationType != null)
			return false;
	} else if (!operationType.equals(other.operationType))
		return false;
	if (slicer == null) {
		if (other.slicer != null)
			return false;
	} else if (!slicer.equals(other.slicer))
		return false;
	if (strategy == null) {
		if (other.strategy != null)
			return false;
	} else if (!strategy.equals(other.strategy))
		return false;
	return true;
}


}
