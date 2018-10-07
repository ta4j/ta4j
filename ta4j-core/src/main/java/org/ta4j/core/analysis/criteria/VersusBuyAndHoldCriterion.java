package org.ta4j.core.analysis.criteria;

import org.ta4j.core.*;
import org.ta4j.core.num.Num;

/**
 * Versus "buy and hold" criterion.
 * </p>
 * Compares the value of a provided {@link AnalysisCriterion criterion} with the value of a {@link BuyAndHoldCriterion "buy and hold" criterion}.
 */
public class VersusBuyAndHoldCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion criterion;

    /**
     * Constructor.
     * @param criterion an analysis criterion to be compared
     */
    public VersusBuyAndHoldCriterion(AnalysisCriterion criterion) {
        this.criterion = criterion;
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        TradingRecord fakeRecord = new BaseTradingRecord();
        fakeRecord.enter(series.getBeginIndex());
        fakeRecord.exit(series.getEndIndex());

        return criterion.calculate(series, tradingRecord).dividedBy(criterion.calculate(series, fakeRecord));
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        TradingRecord fakeRecord = new BaseTradingRecord();
        fakeRecord.enter(series.getBeginIndex());
        fakeRecord.exit(series.getEndIndex());

        return criterion.calculate(series, trade).dividedBy(criterion.calculate(series, fakeRecord));
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
