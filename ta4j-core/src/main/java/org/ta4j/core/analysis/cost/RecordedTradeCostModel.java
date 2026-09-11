/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import java.util.List;
import java.util.Objects;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Cost model that uses recorded trade costs (fees) instead of recomputing them.
 *
 * @since 0.22.2
 */
public final class RecordedTradeCostModel implements CostModel {

    /** Shared instance. */
    public static final RecordedTradeCostModel INSTANCE = new RecordedTradeCostModel();

    private RecordedTradeCostModel() {
    }

    @Override
    public Num calculate(Position position, int finalIndex) {
        Trade entry = position == null ? null : position.getEntry();
        Trade exit = position == null ? null : position.getExit();
        Num zero = zeroFor(entry, exit);
        if (entry == null) {
            return zero;
        }
        Num total = entry.getIndex() <= finalIndex ? entry.getCost() : zero;
        if (exit != null && exit.getIndex() <= finalIndex) {
            total = total.plus(exit.getCost());
        }
        return total;
    }

    @Override
    public Num calculate(Position position) {
        Trade entry = position == null ? null : position.getEntry();
        Trade exit = position == null ? null : position.getExit();
        Num zero = zeroFor(entry, exit);
        if (entry == null) {
            return zero;
        }
        if (exit == null) {
            return entry.getCost();
        }
        return entry.getCost().plus(exit.getCost());
    }

    @Override
    public Num calculate(Num price, Num amount) {
        return price == null ? DoubleNumFactory.getInstance().zero() : price.getNumFactory().zero();
    }

    @Override
    public Num calculate(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        if (fill.futuresContract() == null) {
            return fill.fee();
        }
        if (!fill.hasRecordedFees()) {
            throw new IllegalArgumentException(
                    "native fill has no recorded fees; configure a modeled transaction cost or supply fees");
        }
        return fill.fee();
    }

    @Override
    public List<TradeFee> calculateFees(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        if (fill.futuresContract() == null) {
            throw new IllegalArgumentException("fee components are only defined for futures fills");
        }
        if (!fill.hasRecordedFees()) {
            throw new IllegalArgumentException(
                    "native fill has no recorded fees; configure a modeled transaction cost or supply fees");
        }
        return fill.fees();
    }

    @Override
    public boolean equals(CostModel otherModel) {
        return otherModel instanceof RecordedTradeCostModel;
    }

    private Num zeroFor(Trade entry, Trade exit) {
        if (entry != null) {
            return entry.getCost().getNumFactory().zero();
        }
        if (exit != null) {
            return exit.getCost().getNumFactory().zero();
        }
        return DoubleNumFactory.getInstance().zero();
    }
}
