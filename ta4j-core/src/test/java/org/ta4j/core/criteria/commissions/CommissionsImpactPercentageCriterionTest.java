/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.commissions;

import java.util.stream.Stream;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CommissionsImpactPercentageCriterionTest extends AbstractCriterionTest {

    public CommissionsImpactPercentageCriterionTest(NumFactory numFactory) {
        super(params -> new CommissionsImpactPercentageCriterion(), numFactory);
    }

    @Test
    public void calculateReturnsPercentageForPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120).build();
        var costModel = new FixedTransactionCostModel(1.5);
        var amount = numFactory.one();

        var entry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount, costModel);
        var exit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount, costModel);
        var position = new Position(entry, exit);

        var criterion = getCriterion();
        var result = criterion.calculate(series, position);

        var gross = position.getGrossProfit().abs();
        var commission = position.getPositionCost().abs();
        var expected = commission.dividedBy(gross);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateReturnsZeroWhenGrossIsZero() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 100).build();
        var costModel = new FixedTransactionCostModel(2.0);
        var amount = numFactory.one();

        var entry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount, costModel);
        var exit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount, costModel);
        var position = new Position(entry, exit);

        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, position));
    }

    @Test
    public void calculateReturnsZeroForNewPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120).build();
        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, new Position()));
    }

    @Test
    public void calculateUsesAggregatedProfitAndCommissionFromRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 90, 80, 70).build();
        var costModel = new FixedTransactionCostModel(1.0);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());
        var amount = numFactory.one();

        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount);

        record.enter(2, series.getBar(2).getClosePrice(), amount);
        record.exit(3, series.getBar(3).getClosePrice(), amount);

        record.enter(4, series.getBar(4).getClosePrice(), amount);

        var criterion = getCriterion();
        var result = criterion.calculate(series, record);

        var zero = numFactory.zero();
        var totalGross = record.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(Position::getGrossProfit)
                .reduce(zero, Num::plus)
                .abs();
        var totalCommission = Stream.concat(record.getPositions().stream(), Stream.of(record.getCurrentPosition()))
                .map(p -> record.getTransactionCostModel().calculate(p).abs())
                .reduce(zero, Num::plus);
        var expected = totalGross.isZero() ? zero : totalCommission.dividedBy(totalGross);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateReturnsPercentageWhenAggregatedGrossIsNegative() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120, 90).build();
        var costModel = new FixedTransactionCostModel(0.75);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());
        var amount = numFactory.one();

        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount);

        record.enter(2, series.getBar(2).getClosePrice(), amount);
        record.exit(3, series.getBar(3).getClosePrice(), amount);

        var criterion = getCriterion();
        var result = criterion.calculate(series, record);

        var totalGross = record.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(Position::getGrossProfit)
                .reduce(numFactory.zero(), Num::plus)
                .abs();
        var totalCommission = record.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(p -> record.getTransactionCostModel().calculate(p).abs())
                .reduce(numFactory.zero(), Num::plus);
        var expected = totalCommission.dividedBy(totalGross);

        assertTrue(record.getPositions()
                .stream()
                .map(Position::getGrossProfit)
                .reduce(numFactory.zero(), Num::plus)
                .isNegative());
        assertNumEquals(expected, result);
    }

    @Test
    public void calculateReturnsZeroWhenRecordGrossIsZero() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 130, 110).build();
        var costModel = new FixedTransactionCostModel(0.5);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());
        var amount = numFactory.one();

        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount);

        record.enter(2, series.getBar(2).getClosePrice(), amount);
        record.exit(3, series.getBar(3).getClosePrice(), amount);

        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, record));
    }

    @Test
    public void betterThanPrefersLowerImpact() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.one(), numFactory.two()));
        assertFalse(criterion.betterThan(numFactory.two(), numFactory.one()));
    }
}
