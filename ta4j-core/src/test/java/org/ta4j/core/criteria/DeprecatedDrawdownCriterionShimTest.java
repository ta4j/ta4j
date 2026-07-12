/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@SuppressWarnings("removal")
public class DeprecatedDrawdownCriterionShimTest extends AbstractCriterionTest {

    public DeprecatedDrawdownCriterionShimTest(NumFactory numFactory) {
        super(params -> new MaximumDrawdownCriterion(), numFactory);
    }

    @Test
    public void maximumDrawdownShimMatchesMovedCriterion() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 120, 80, 90, 130)
                .build();
        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series), Trade.sellAt(4, series));
        MaximumDrawdownCriterion shim = new MaximumDrawdownCriterion();
        org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion moved = new org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion();

        assertThat(shim.calculate(series, tradingRecord)).isEqualByComparingTo(moved.calculate(series, tradingRecord));
        assertThat(shim.calculate(series, tradingRecord, AnalysisWindow.barRange(1, 3)))
                .isEqualByComparingTo(moved.calculate(series, tradingRecord, AnalysisWindow.barRange(1, 3)));
        assertThat(shim.betterThan(numFactory.one(), numFactory.two()))
                .isEqualTo(moved.betterThan(numFactory.one(), numFactory.two()));
    }

    @Test
    public void returnOverMaxDrawdownShimMatchesMovedCriterion() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80, 160).build();
        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));
        PositionlessRecordValues values = calculateReturnOverDrawdown(series, tradingRecord);

        assertThat(values.shimValue()).isEqualByComparingTo(values.movedValue());
        assertThat(values.shim().getReturnRepresentation()).isEqualTo(values.moved().getReturnRepresentation());
        assertThat(values.shim().betterThan(numFactory.two(), numFactory.one()))
                .isEqualTo(values.moved().betterThan(numFactory.two(), numFactory.one()));
    }

    private PositionlessRecordValues calculateReturnOverDrawdown(BarSeries series, BaseTradingRecord tradingRecord) {
        ReturnOverMaxDrawdownCriterion shim = new ReturnOverMaxDrawdownCriterion();
        org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion moved = new org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion();
        Num shimValue = shim.calculate(series, tradingRecord);
        Num movedValue = moved.calculate(series, tradingRecord);
        return new PositionlessRecordValues(shim, moved, shimValue, movedValue);
    }

    private record PositionlessRecordValues(ReturnOverMaxDrawdownCriterion shim,
            org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion moved, Num shimValue, Num movedValue) {
    }
}
