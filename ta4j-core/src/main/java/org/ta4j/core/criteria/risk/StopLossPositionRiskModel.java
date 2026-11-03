/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.risk;

import java.util.List;
import java.util.ArrayList;

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
     * economics instead: the loss is the contract profit of the residual executed
     * entry quantity between the executed net entry price and the stop price. The
     * executed net entry price embeds the recorded entry fees for executed fills,
     * so the fees paid on entry are allocated to the residual exposure, and inverse
     * price sensitivity and the contract multiplier are applied by the contract
     * rather than by a quote-price gap. Fully exited futures positions retain their
     * historical initial entry exposure for R-multiple compatibility.
     * </p>
     *
     * <p>
     * This method returns zero when the position context is missing or unusable
     * (missing entry, NaN values, zero amount, unavailable stop price, or
     * nonpositive futures stop price).
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
        FuturesContract contract = position.getFuturesContract();
        Trade entry = position.getEntry();
        Trade effectiveEntry = contract == null ? entry : executedEntryTrade(entry);
        if (effectiveEntry == null) {
            return series.numFactory().zero();
        }
        Num entryPrice = effectiveEntry.getNetPrice();
        Num amount = contract == null ? entry.getAmount() : effectiveEntry.getAmount();
        if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(amount) || amount.isZero()) {
            return series.numFactory().zero();
        }

        Position stopLossPosition = position;
        if (effectiveEntry != entry) {
            Trade exit = position.getExit();
            stopLossPosition = exit == null
                    ? new Position(effectiveEntry, position.getTransactionCostModel(), position.getHoldingCostModel())
                    : new Position(effectiveEntry, exit, position.getTransactionCostModel(),
                            position.getHoldingCostModel());
        }
        Num stopPrice = stopLossModel.stopPrice(series, stopLossPosition);
        if (Num.isNaNOrNull(stopPrice)) {
            return series.numFactory().zero();
        }
        if (contract == null) {
            Num perUnitRisk = entryPrice.minus(stopPrice).abs();
            return perUnitRisk.multipliedBy(amount.abs());
        }
        if (!stopPrice.isPositive()) {
            return series.numFactory().zero();
        }
        Num residualAmount = residualFuturesAmount(position.getExit(), amount);
        return contract.profit(entry.getType(), residualAmount.abs(), entryPrice, stopPrice).abs();
    }

    private static Trade executedEntryTrade(Trade trade) {
        List<TradeFill> fills = Trade.executionFillsOf(trade);
        if (fills.isEmpty()) {
            return trade;
        }
        boolean hasDeferredFill = false;
        for (TradeFill fill : fills) {
            if (fill.index() < 0) {
                hasDeferredFill = true;
                break;
            }
        }
        if (!hasDeferredFill) {
            return trade;
        }
        List<TradeFill> executedFills = new ArrayList<>(fills.size());
        for (TradeFill fill : fills) {
            if (fill.index() >= 0) {
                executedFills.add(fill);
            }
        }
        if (executedFills.isEmpty()) {
            return null;
        }
        return Trade.fromFills(trade.getType(), executedFills, trade.getCostModel());
    }

    private static Num residualFuturesAmount(Trade exit, Num executedEntryAmount) {
        if (exit == null) {
            return executedEntryAmount;
        }
        Num executedExitAmount = executedEntryAmount.getNumFactory().zero();
        for (TradeFill fill : Trade.executionFillsOf(exit)) {
            if (fill.index() >= 0) {
                executedExitAmount = executedExitAmount
                        .plus(executedEntryAmount.getNumFactory().numOf(fill.amount().getDelegate()));
            }
        }
        if (executedExitAmount.isPositive() && executedExitAmount.isLessThan(executedEntryAmount)) {
            return executedEntryAmount.minus(executedExitAmount);
        }
        return executedEntryAmount;
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
            Trade executedEntry = executedEntryTrade(position.getEntry());
            if (executedEntry == null) {
                return null;
            }
            Num entryPrice = executedEntry.getNetPrice();
            if (Num.isNaNOrNull(entryPrice)) {
                return null;
            }
            Num lossPercent = series.numFactory().numOf(lossPercentage);
            return StopLossRule.stopLossPrice(entryPrice, lossPercent, executedEntry.isBuy());
        };
    }
}
