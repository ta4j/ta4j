/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.helpers.StandardDeviationCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.num.Num;

/**
 * The SQN ("System Quality Number") Criterion.
 *
 * @see <a href=
 *      "https://indextrader.com.au/van-tharps-sqn/">https://indextrader.com.au/van-tharps-sqn/</a>
 */
public class SqnCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion criterion;
    private final StandardDeviationCriterion standardDeviationCriterion;
    private final NumberOfPositionsCriterion numberOfPositionsCriterion = new NumberOfPositionsCriterion();

    /**
     * The number to be used for the part of {@code √(numberOfPositions)} within the
     * SQN-Formula when there are more than 100 trades. If this value is
     * {@code null}, then the number of positions calculated by
     * {@link #numberOfPositionsCriterion} is used instead.
     */
    private final Integer nPositions;

    /**
     * Constructor with {@code criterion} = {@link NetProfitLossCriterion}.
     */
    public SqnCriterion() {
        this(new NetProfitLossCriterion());
    }

    /**
     * Constructor.
     *
     * @param criterion the Criterion (e.g. NetProfitLossCriterion or
     *                  ExpectancyCriterion)
     */
    public SqnCriterion(AnalysisCriterion criterion) {
        this(criterion, null);
    }

    /**
     * Constructor.
     *
     * @param criterion  the Criterion (e.g. NetProfitLossCriterion or
     *                   ExpectancyCriterion)
     * @param nPositions the {@link #nPositions} (optional)
     */
    public SqnCriterion(AnalysisCriterion criterion, Integer nPositions) {
        this.criterion = criterion;
        this.nPositions = nPositions;
        this.standardDeviationCriterion = new StandardDeviationCriterion(criterion);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num stdDevPnl = standardDeviationCriterion.calculate(series, position);
        if (stdDevPnl.isZero()) {
            return series.numFactory().zero();
        }
        // SQN = (Average (PnL) / StdDev(PnL)) * SquareRoot(NumberOfTrades)
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, position);
        Num pnl = criterion.calculate(series, position);
        Num avgPnl = pnl.dividedBy(numberOfPositions);
        return avgPnl.dividedBy(stdDevPnl).multipliedBy(numberOfPositions.sqrt());
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty()) {
            return series.numFactory().zero();
        }
        Num stdDevPnl = standardDeviationCriterion.calculate(series, tradingRecord);
        if (stdDevPnl.isZero()) {
            return series.numFactory().zero();
        }

        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        Num pnl = criterion.calculate(series, tradingRecord);
        Num avgPnl = pnl.dividedBy(numberOfPositions);
        if (nPositions != null && numberOfPositions.isGreaterThan(series.numFactory().hundred())) {
            numberOfPositions = series.numFactory().numOf(nPositions);
        }
        // SQN = (Average (PnL) / StdDev(PnL)) * SquareRoot(NumberOfTrades)
        return avgPnl.dividedBy(stdDevPnl).multipliedBy(numberOfPositions.sqrt());
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
