/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/**
 * Net return criterion.
 *
 * <p>
 * Calculates the net return of positions, where trading costs are deducted from
 * the calculation. The output format is controlled by the
 * {@link ReturnRepresentation} specified in the constructor or the global
 * default from {@link org.ta4j.core.criteria.ReturnRepresentationPolicy}.
 *
 * <p>
 * Examples for a +12% return:
 * <ul>
 * <li>MULTIPLICATIVE: 1.12 (includes base, growth factor)
 * <li>DECIMAL: 0.12 (excludes base, decimal fraction)
 * <li>PERCENTAGE: 12.0 (percentage value)
 * <li>LOG: ln(1.12) ≈ 0.113 (logarithmic return)
 * </ul>
 *
 * <p>
 * The return of the provided {@link Position position(s)} over the provided
 * {@link BarSeries series}.
 *
 * <p>
 * A native futures {@link TradingRecord} is one financed account, so its return
 * is {@code 1 + sum(realized net profit) / initialCapital} expressed in the
 * configured {@link ReturnRepresentation} rather than the product of the
 * matched position returns. Single futures {@link Position positions} keep
 * their unlevered normalization by the original entry settlement notional.
 *
 * @see ReturnRepresentation
 * @see org.ta4j.core.criteria.ReturnRepresentationPolicy
 */
public class NetReturnCriterion extends AbstractReturnCriterion {

    public NetReturnCriterion() {
        super();
    }

    public NetReturnCriterion(ReturnRepresentation representation) {
        super(representation);
    }

    @Deprecated(since = "0.24.0")
    public NetReturnCriterion(boolean addBase) {
        super(addBase);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (FuturesRecordReturnSupport.isFuturesRecord(tradingRecord)) {
            Num totalReturn = FuturesRecordReturnSupport.totalReturn(series, tradingRecord, false);
            return returnRepresentation.toRepresentationFromTotalReturn(totalReturn);
        }
        return super.calculate(series, tradingRecord);
    }

    @Override
    protected Num calculateReturn(BarSeries series, Position position) {
        Trade entry = position.getEntry();
        Num amount = entry.getAmount();
        Num one = series.numFactory().one();
        FuturesContract contract = position.getFuturesContract();
        if (contract != null) {
            Trade exit = position.getExit();
            Num quantity = exit == null ? amount : exit.getAmount();
            Num entryNotional = contract.settlementNotional(quantity, entry.getPricePerAsset());
            if (entryNotional.isZero()) {
                return one;
            }
            Num profit = position.getProfit();
            return profit.dividedBy(entryNotional).plus(one);
        }
        Num netPrice = entry.getNetPrice();
        Num entryValue = netPrice.multipliedBy(amount);
        if (entryValue.isZero()) {
            return one;
        }
        Num profit = position.getProfit();
        return profit.dividedBy(entryValue).plus(one);
    }

}
