/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class RecordedTradeCostModelTest {

    @Test
    public void calculateSpotPositionAtFinalIndexUsesOnlyExecutedFillFees() {
        TradeFill firstFill = new TradeFill(0, Instant.EPOCH, DoubleNum.valueOf(100), DoubleNum.valueOf(1),
                DoubleNum.valueOf(0.1), ExecutionSide.BUY, null, null);
        TradeFill secondFill = new TradeFill(2, Instant.EPOCH.plusSeconds(120), DoubleNum.valueOf(110),
                DoubleNum.valueOf(1), DoubleNum.valueOf(0.2), ExecutionSide.BUY, null, null);
        Trade entry = Trade.fromFills(Trade.TradeType.BUY, List.of(firstFill, secondFill),
                RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

        Num costAtFirstFill = RecordedTradeCostModel.INSTANCE.calculate(position, 0);
        Num costAtSecondFill = RecordedTradeCostModel.INSTANCE.calculate(position, 2);

        assertNumEquals(0.1, costAtFirstFill);
        assertNumEquals(0.3, costAtSecondFill);
    }
}
