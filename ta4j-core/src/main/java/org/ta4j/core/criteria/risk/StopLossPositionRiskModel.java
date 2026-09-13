/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.risk;

import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.StopLossPriceModel;
import org.ta4j.core.rules.StopLossRule;

/**
 * Computes per-trade risk using a stop-loss price model.
 *
 * <p>
 * The risk amount is derived from the stop price calculated by the supplied
 * {@link StopLossPriceModel} (for example, {@link StopLossRule} or
 * {@code FixedAmountStopLossRule}) and then multiplied by position size to
 * yield a monetary exposure.
 *
 * <p>
 * See
 * <a href="https://www.investopedia.com/terms/s/stop-lossorder.asp">Stop-loss
 * orders</a> for background on stop-loss concepts.
 *
 * @since 0.22.3
 */
public final class StopLossPositionRiskModel implements PositionRiskModel {

    private final StopLossPriceModel stopLossModel;

    /**
     * Constructor.
     *
     * @param lossPercentage stop-loss percentage from entry price (for example,
     *                       {@code 5} for 5%)
     */
    public StopLossPositionRiskModel(Number lossPercentage) {
        this(fixedPercentageModel(lossPercentage));
    }

    /**
     * Constructor.
     *
     * @param stopLossModel stop-loss price model to use for risk calculations
     * @since 0.22.3
     */
    public StopLossPositionRiskModel(StopLossPriceModel stopLossModel) {
        if (stopLossModel == null) {
            throw new IllegalArgumentException("stopLossModel must not be null");
        }
        this.stopLossModel = stopLossModel;
    }

    /**
     * Computes monetary risk for a position as absolute price distance to stop
     * times position amount.
     *
     * <p>
     * A native futures position is evaluated through the contract's settlement
     * economics instead: the loss is the contract profit of the unclosed contracts
     * between the net entry price and the stop price. The net entry price already
     * embeds the recorded entry fees for both linear and inverse settlement, so the
     * fees paid on entry are part of the loss, and inverse price sensitivity and
     * the contract multiplier are applied by the contract rather than by a
     * quote-price gap.
     * </p>
     *
     * <p>
     * This method returns zero when the position context is missing or unusable
     * (missing entry, NaN values, zero amount, or unavailable stop price).
     *
     * @param series   the bar series, must not be {@code null}
     * @param position the position to evaluate
     * @return monetary risk amount for the position
     * @since 0.22.3
     */
    @Override
    public Num risk(BarSeries series, Position position) {
        if (series == null) {
            throw new IllegalArgumentException("series must not be null");
        }
        if (position == null || position.getEntry() == null) {
            return series.numFactory().zero();
        }
        Num entryPrice = position.getEntry().getNetPrice();
        Num amount = position.getEntry().getAmount();
        if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(amount) || amount.isZero()) {
            return series.numFactory().zero();
        }

        Num stopPrice = stopLossModel.stopPrice(series, position);
        if (Num.isNaNOrNull(stopPrice)) {
            return series.numFactory().zero();
        }
        FuturesContract contract = position.getFuturesContract();
        if (contract == null) {
            Num perUnitRisk = entryPrice.minus(stopPrice).abs();
            return perUnitRisk.multipliedBy(amount.abs());
        }
        Num remainingAmount = remainingAmount(position, amount);
        if (remainingAmount.isZero()) {
            return series.numFactory().zero();
        }
        return contract.profit(position.getEntry().getType(), remainingAmount, entryPrice, stopPrice).abs();
    }

    private static Num remainingAmount(Position position, Num entryAmount) {
        Num executedEntryAmount = executedAmount(position.getEntry(), entryAmount);
        Trade exit = position.getExit();
        if (exit == null) {
            return executedEntryAmount.abs();
        }
        Num executedExitAmount = executedAmount(exit, exit.getAmount());
        Num remaining = executedEntryAmount.abs().minus(executedExitAmount.abs());
        return remaining.isPositive() ? remaining : entryAmount.getNumFactory().zero();
    }

    private static Num executedAmount(Trade trade, Num fallback) {
        List<TradeFill> fills = trade.getFills();
        if (fills.isEmpty()) {
            return fallback;
        }
        Num total = fallback.getNumFactory().zero();
        for (TradeFill fill : fills) {
            if (fill.index() >= 0) {
                total = total.plus(total.getNumFactory().numOf(fill.amount().getDelegate()));
            }
        }
        return total;
    }

    /**
     * Creates a stop-loss model from a fixed percentage.
     *
     * @param lossPercentage fixed stop-loss percentage
     * @return a stop-loss model based on the provided percentage
     */
    private static StopLossPriceModel fixedPercentageModel(Number lossPercentage) {
        if (lossPercentage == null) {
            throw new IllegalArgumentException("lossPercentage must not be null");
        }
        if (Double.isNaN(lossPercentage.doubleValue()) || lossPercentage.doubleValue() <= 0) {
            throw new IllegalArgumentException("lossPercentage must be positive");
        }
        return (series, position) -> {
            if (series == null || position == null || position.getEntry() == null) {
                return null;
            }
            Num entryPrice = position.getEntry().getNetPrice();
            if (Num.isNaNOrNull(entryPrice)) {
                return null;
            }
            Num lossPercent = series.numFactory().numOf(lossPercentage);
            return StopLossRule.stopLossPrice(entryPrice, lossPercent, position.getEntry().isBuy());
        };
    }
}
