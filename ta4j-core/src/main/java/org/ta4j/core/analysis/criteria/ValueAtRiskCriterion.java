package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.Returns;
import org.ta4j.core.num.Num;

import java.util.Collections;
import java.util.List;
/**
 * Value at Risk criterion.
 * </p>
 * @see <a href="https://en.wikipedia.org/wiki/Value_at_risk">https://en.wikipedia.org/wiki/Value_at_risk</a>
 */
public class ValueAtRiskCriterion extends AbstractAnalysisCriterion {
    /**
     * Confidence level as absolute value (e.g. 0.95)
     */
    private final Double confidence;

    /**
     * Constructor
     *
     * @param confidence the confidence level
     */
    public ValueAtRiskCriterion(Double confidence) {
        this.confidence = confidence;
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        Returns returns = new Returns(series, tradingRecord, Returns.ReturnType.LOG);
        return calculateVaR(returns, confidence);
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        if (trade != null && trade.isClosed()) {
            Returns returns = new Returns(series, trade, Returns.ReturnType.LOG);
            return calculateVaR(returns, confidence);
        }
        return series.numOf(0);
    }

    /**
     * Calculates the VaR on the return series
     * @param returns the corresponding returns
     * @param confidence the confidence level
     * @return the relative Value at Risk
     */
    private static Num calculateVaR(Returns returns, double confidence) {
        Num zero = returns.numOf(0);
        // select non-NaN returns
        List<Num> returnRates = returns.getValues().subList(1, returns.getSize() + 1);
        Num var = zero;
        if (!returnRates.isEmpty()) {
            // F(x_var) >= alpha (=1-confidence)
            int nInBody = (int) (returns.getSize() * confidence);
            int nInTail = returns.getSize() - nInBody;

            // The series is not empty, nInTail > 0
            Collections.sort(returnRates);
            var = returnRates.get(nInTail-1);

            // VaR is non-positive
            if (var.isGreaterThan(zero)) { var = zero; }
        }
        return var;
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        // because it represents a loss, VaR is non-positive
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
