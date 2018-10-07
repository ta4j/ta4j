package org.ta4j.core.analysis.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Average profit criterion.
 * </p>
 * The {@link TotalProfitCriterion total profit} over the {@link NumberOfBarsCriterion number of bars}.
 */
public class AverageProfitCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion totalProfit = new TotalProfitCriterion();

    private AnalysisCriterion numberOfBars = new NumberOfBarsCriterion();

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        Num bars = numberOfBars.calculate(series, tradingRecord);
        if (bars.isEqual(series.numOf(0))) {
            return series.numOf(1);
        }

        return totalProfit.calculate(series, tradingRecord).pow(series.numOf(1).dividedBy(bars));
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        Num bars = numberOfBars.calculate(series, trade);
        if (bars.isEqual(series.numOf(0))) {
            return series.numOf(1);
        }

        return totalProfit.calculate(series, trade).pow(series.numOf(1).dividedBy(bars));
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
