package eu.verdelhan.ta4j.runners;

import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Runner;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class HistoryRunner implements Runner {

    private OperationType operationType;

    private TimeSeriesSlicer slicer;

    private Strategy strategy;

    private ArrayList<List<Trade>> listTradesResults;

    private static final Logger LOG = Logger.getLogger(HistoryRunner.class);

    public HistoryRunner(OperationType type, TimeSeriesSlicer slicer, Strategy strategy) {
        if ((type == null) || (slicer == null) || (strategy == null)) {
            throw new NullPointerException();
        }
        this.slicer = slicer;
        this.strategy = strategy;
        operationType = type;
        LOG.setLevel(Level.WARN);
        listTradesResults = new ArrayList<List<Trade>>();
    }

    public HistoryRunner(TimeSeriesSlicer slicer, Strategy strategy) {
        this(OperationType.BUY, slicer, strategy);
    }

    @Override
    public List<Trade> run(int slicePosition) {
        if (listTradesResults.size() < slicePosition) {
            listTradesResults.add(run(slicePosition - 1));
        } else if (listTradesResults.size() > slicePosition) {
            return listTradesResults.get(slicePosition);
        }

        int begin = 0;
        int end = 0;
        if (listTradesResults.isEmpty()) {
            begin = slicer.getSlice(slicePosition).getBegin();
            end = slicer.getSlice(slicePosition).getEnd();
        } else {

            end = slicer.getSlice(slicePosition).getEnd();

            int i = listTradesResults.size() - 1;
            List<Trade> lastTrades = listTradesResults.get(i);
            while ((lastTrades.isEmpty()) && (i > 0)) {
                i--;
                lastTrades = listTradesResults.get(i);
            }

            if (i <= 0) {
                begin = slicer.getSlice(slicePosition).getBegin();

            } else {
                Trade lastTrade = lastTrades.get(lastTrades.size() - 1);
                begin = lastTrade.getExit().getIndex() + 1;

                if (begin > end) {
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
            while (slicer.getNumberOfSlices() > (slicePosition + j)) {
                int start = Math.max(slicer.getSlice(slicePosition + j).getBegin(), end);
                int last = slicer.getSlice(slicePosition + j).getEnd();

                for (int i = start; i <= last; i++) {
                    if (strategy.shouldOperate(lastTrade, i)) {
                        lastTrade.operate(i);
                        break;
                    }
                }
                if (lastTrade.isClosed()) {
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
}
