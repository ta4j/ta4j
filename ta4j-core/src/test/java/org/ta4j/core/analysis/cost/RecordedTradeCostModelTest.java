/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class RecordedTradeCostModelTest {

    @Test
    public void recordedCostSpotRideAlongExcludesFutureEntryFillFees() {
        NumFactory factory = DoubleNumFactory.getInstance();
        TradeFill first = new TradeFill(2, null, factory.hundred(), factory.one(), factory.one(), ExecutionSide.BUY,
                null, null);
        TradeFill second = new TradeFill(8, null, factory.hundred(), factory.one(), factory.one(), ExecutionSide.BUY,
                null, null);
        Trade entry = Trade.fromFills(Trade.TradeType.BUY, List.of(first, second), RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

        assertNumEquals(DoubleNum.valueOf(0), position.getPositionCost(1));
        assertNumEquals(DoubleNum.valueOf(1), position.getPositionCost(5));
        assertNumEquals(DoubleNum.valueOf(2), position.getPositionCost(8));
    }
}
