package eu.verdelhan.ta4j.analysis;

import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TAUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CashFlow {

    private final TimeSeries timeSeries;

    private final List<Trade> trades;

    private List<BigDecimal> values;

    public CashFlow(TimeSeries timeSeries, List<Trade> trades) {
        this.timeSeries = timeSeries;
        this.trades = trades;
        values = new ArrayList<BigDecimal>();
        values.add(BigDecimal.ONE);
        calculate();
    }

    public BigDecimal getValue(int index) {
        return values.get(index);
    }

    public int getSize() {
        return timeSeries.getSize();
    }

    /**
     * Calculates the cash flow.
     */
    private void calculate() {

        for (Trade trade : trades) {
            // For each trade...
            int begin = trade.getEntry().getIndex() + 1;
            if (begin > values.size()) {
                values.addAll(Collections.nCopies(begin - values.size(), values.get(values.size() - 1)));
            }
            int end = trade.getExit().getIndex();
            for (int i = Math.max(begin, 1); i <= end; i++) {
                BigDecimal ratio;
                if (trade.getEntry().getType().equals(OperationType.BUY)) {
                    ratio = timeSeries.getTick(i).getClosePrice().divide(timeSeries.getTick(trade.getEntry().getIndex()).getClosePrice(), TAUtils.MATH_CONTEXT);
                } else {
                    ratio = timeSeries.getTick(trade.getEntry().getIndex()).getClosePrice().divide(timeSeries.getTick(i).getClosePrice(), TAUtils.MATH_CONTEXT);
                }
                values.add(values.get(trade.getEntry().getIndex()).multiply(ratio, TAUtils.MATH_CONTEXT));
            }
        }
        if ((timeSeries.getEnd() - values.size()) >= 0) {
            values.addAll(Collections.nCopies((timeSeries.getEnd() - values.size()) + 1, values.get(values.size() - 1)));
        }
    }
}