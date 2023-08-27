package org.ta4j.sparx.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class CompoundedAnnualGrowthReturnCriteriaTest extends AbstractCriterionTest {
    public CompoundedAnnualGrowthReturnCriteriaTest(Function<Number, Num> numFunction) {
        super((params) -> new CompoundedAnnualGrowthReturnCriteria(), numFunction);
    }

    @Test
    public void benchMark() {
        double startingValue = 100;
        double endingValue = 150;
        int startingYear = 2015;
        int endingYear = 2018;
        int holdingPeriod = endingYear - startingYear;
        double cagr = Math.pow((endingValue / startingValue), (1.0 / holdingPeriod)) - 1;
        System.out.printf("The compounded annual growth rate is %.2f%%.", cagr * 100);
    }

    @Test
    public void calculateWithWinningPosition() {
        ZonedDateTime startTimestamp = ZonedDateTime.of(2015, 1, 1, 8, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime endTimestamp = ZonedDateTime.of(2018, 1, 1, 8, 0, 0, 0, ZoneId.systemDefault());

        List<Bar> barSeries = new ArrayList<>();
        MockBar bar1 = new MockBar(startTimestamp, 100, numFunction);
        MockBar bar2 = new MockBar(endTimestamp, 150, numFunction);
        barSeries.add(bar1);
        barSeries.add(bar2);
        BaseBarSeries series = new BaseBarSeries(barSeries);

        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.buyAt(1, series));
        AnalysisCriterion criterion = getCriterion();
        Num cagr = criterion.calculate(series, tradingRecord).multipliedBy(DoubleNum.valueOf(100));

        assertNumEquals("14.47", cagr.numOf(cagr.toString(), 4));
    }

}
