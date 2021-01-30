package org.ta4j.core.analysis.criteria;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Average profit or loss in percent per time criterion.
 * 
 * <p>
 * Calculates the average profit or loss in percent achieved within a time unit.
 * For example, it answers the question: "In 100 days one achieved 5% profit,
 * what is the average profit per 10 days?"
 */
public class ProfitLossPercentagePerTimeCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion pnlPercentageCriterion = new ProfitLossPercentageCriterion();

    private final ChronoUnit timeUnit;

    /**
     * Constructor.
     * 
     * <p>
     * Average profit per day.
     */
    public ProfitLossPercentagePerTimeCriterion() {
        this.timeUnit = ChronoUnit.DAYS;
    }

    /**
     * Constructor.
     * 
     * <p>
     * Average profit per time unit.
     * 
     * @param timeUnit the time unit
     */
    public ProfitLossPercentagePerTimeCriterion(ChronoUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num pnlPercentage = pnlPercentageCriterion.calculate(series, tradingRecord);
        return calculate(series, pnlPercentage);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num pnlPercentage = pnlPercentageCriterion.calculate(series, position);
        return calculate(series, pnlPercentage);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    private Num calculate(BarSeries series, Num pnlPercentage) {
        ZonedDateTime startTime = series.getFirstBar().getEndTime();
        ZonedDateTime endTime = series.getLastBar().getEndTime();
        Long duration = timeUnit.between(startTime, endTime);
        if (duration == 0)
            return series.numOf(0);
        return series.numOf(timeUnit.getDuration().getSeconds() / Double.valueOf(duration)).multipliedBy(pnlPercentage);
    }

}
