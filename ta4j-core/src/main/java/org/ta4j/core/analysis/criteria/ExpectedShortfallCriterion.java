package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.Returns;
import org.ta4j.core.num.Num;

import java.util.Collections;
import java.util.List;
/**
 * Expected Shortfall criterion.
 * </p>
 * @see <a href="https://en.wikipedia.org/wiki/Expected_shortfall">https://en.wikipedia.org/wiki/Expected_shortfall</a>
 *
 * Measures the expected shortfall of the strategy log-return time-series
 */
public class ExpectedShortfallCriterion extends AbstractAnalysisCriterion {
    /**
     * Confidence level as absolute value (e.g. 0.95)
     */
    private final Double confidence;

    /**
     * Constructor
     *
     * @param confidence the confidence level
     */
    public ExpectedShortfallCriterion(Double confidence) {
        this.confidence = confidence;
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        Returns returns = new Returns(series, tradingRecord, Returns.ReturnType.LOG);
        return calculateES(returns, confidence);
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        if (trade != null && trade.getEntry() != null && trade.getExit() != null) {
            Returns returns = new Returns(series, trade, Returns.ReturnType.LOG);
            return calculateES(returns, confidence);
        }
        return series.numOf(0);
    }

    /**
     * Calculates the Expected Shortfall on the return series
     * @param returns the corresponding returns
     * @param confidence the confidence level
     * @return the relative Expected Shortfall
     */
    private static Num calculateES(Returns returns, double confidence) {
        // select non-NaN returns
        List<Num> returnRates = returns.getValues().subList(1, returns.getSize() + 1);
        Num zero = returns.numOf(0);
        Num expectedShortfall = zero;
        if (!returnRates.isEmpty()) {
            // F(x_var) >= alpha (=1-confidence)
            int nInBody = (int) (returns.getSize() * confidence);
            int nInTail = returns.getSize() - nInBody;

            // calculate average tail loss
            Collections.sort(returnRates);
            List<Num> tailEvents = returnRates.subList(0, nInTail);
            Num sum = zero;
            for(int i=0; i < nInTail; i++) {
                sum = sum.plus(tailEvents.get(i));
            }
            expectedShortfall = sum.dividedBy(returns.numOf(nInTail));

            // ES is non-positive
            if (expectedShortfall.isGreaterThan(zero)) {
                expectedShortfall = zero;
            }
        }
        return expectedShortfall;
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
