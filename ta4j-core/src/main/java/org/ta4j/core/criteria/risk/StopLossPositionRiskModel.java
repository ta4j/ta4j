/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.risk;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
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
 * @since 0.22.2
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
     * @since 0.22.2
     */
    public StopLossPositionRiskModel(StopLossPriceModel stopLossModel) {
        if (stopLossModel == null) {
            throw new IllegalArgumentException("stopLossModel must not be null");
        }
        this.stopLossModel = stopLossModel;
    }

    @Override
    public Num risk(BarSeries series, Position position) {
        if (series == null || position == null || position.getEntry() == null) {
            return null;
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
        Num perUnitRisk = entryPrice.minus(stopPrice).abs();
        return perUnitRisk.multipliedBy(amount.abs());
    }

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
            if (lossPercent.isZero() || lossPercent.isNegative()) {
                return series.numFactory().zero();
            }
            return StopLossRule.stopLossPrice(entryPrice, lossPercent, position.getEntry().isBuy());
        };
    }
}
