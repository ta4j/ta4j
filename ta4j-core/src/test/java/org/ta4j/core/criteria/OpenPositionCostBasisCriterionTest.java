/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OpenPositionCostBasisCriterionTest extends AbstractCriterionTest {

    public OpenPositionCostBasisCriterionTest(NumFactory numFactory) {
        super(params -> new OpenPositionCostBasisCriterion(), numFactory);
    }

    @Test
    public void calculateUsesBaseTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        Num expected = numFactory.hundred()
                .plus(numFactory.numOf(110))
                .plus(numFactory.numOf(0.1))
                .plus(numFactory.numOf(0.2));
        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result, 1e-12);
    }

    @Test
    public void calculateUsesCurrentPositionForStandardRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var costModel = new FixedTransactionCostModel(1.5);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());

        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        Num expected = series.getBar(0)
                .getClosePrice()
                .multipliedBy(numFactory.one())
                .plus(costModel.calculate(series.getBar(0).getClosePrice(), numFactory.one()));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void returnsZeroWhenNoOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        var result = getCriterion().calculate(series, record);

        assertNumEquals(numFactory.zero(), result);
    }

    @Test
    public void betterThanPrefersLowerCostBasis() {
        var criterion = getCriterion();

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.two()));
        assertFalse(criterion.betterThan(numFactory.two(), numFactory.one()));
    }

    @Test
    public void includesClosedLotsActiveAtLogicalEnd() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord(false);

        assertNumEquals(numFactory.numOf(210), getCriterion().calculate(series, record));
    }

    @Test
    public void ignoresCurrentLotOpenedAfterLogicalEnd() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord(true);

        assertNumEquals(numFactory.numOf(210), getCriterion().calculate(series, record));
    }

    @Test
    public void treatsPositionExitedAfterSeriesEndAsOpen() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord(false);

        assertNumEquals(numFactory.hundred(), getCriterion().calculate(series, record.getPositions().getFirst()));
    }

    @Test
    public void excludesOpenLotEnteredAfterLogicalEndFromMixedOpenLots() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = openLotsAcrossLogicalEnd();

        assertTrue(record.getPositions().isEmpty());
        assertNumEquals(numFactory.hundred(), getCriterion().calculate(series, record));
    }

    private BaseTradingRecord openLotsAcrossLogicalEnd() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(7, Instant.EPOCH.plusSeconds(7), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        return record;
    }

    private BarSeries multiLotSeries() {
        return ConstrainedSeriesSupport.trailingConstrainedSeries("cost-basis-multi-lot", numFactory, 5, 100d, 110d,
                110d, 110d, 110d, 120d, 120d, 120d, 130d, 130d, 130d, 130d);
    }

    private BaseTradingRecord multiLotRecord(boolean addLaterOpenLot) {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.EPOCH.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        if (addLaterOpenLot) {
            record.operate(new BaseTrade(7, Instant.EPOCH.plusSeconds(7), numFactory.numOf(120), numFactory.one(),
                    numFactory.zero(), ExecutionSide.BUY, null, null));
        }
        record.operate(new BaseTrade(10, Instant.EPOCH.plusSeconds(10), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        record.operate(new BaseTrade(11, Instant.EPOCH.plusSeconds(11), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        return record;
    }
}
