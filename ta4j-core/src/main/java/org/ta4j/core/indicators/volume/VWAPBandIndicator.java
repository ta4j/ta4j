/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * VWAP-based bands computed as VWAP +/- multiplier * standard deviation.
 * <p>
 * The bands mirror Bollinger Bands semantics for VWAP and anchored VWAP use
 * cases, providing a quick read on value areas, stretch and potential mean
 * reversion. Uses the same VWAP window/anchor definition as the supplied
 * indicators.
 *
 * @since 0.19
 */
public class VWAPBandIndicator extends CachedIndicator<Num> {

    /**
     * Band direction to compute relative to VWAP.
     *
     * @since 0.19
     */
    public enum BandType {
        UPPER, LOWER
    }

    private final AbstractVWAPIndicator vwapIndicator;
    private final Indicator<Num> standardDeviationIndicator;
    private final Num multiplier;
    private final transient Indicator<Num> scaledDeviation;
    private final transient Indicator<Num> band;
    private final BandType bandType;

    /**
     * Constructor.
     *
     * @param vwapIndicator              the VWAP indicator
     * @param standardDeviationIndicator the VWAP standard deviation indicator
     * @param multiplier                 number of standard deviations to offset
     * @param bandType                   upper or lower band selection
     *
     * @since 0.19
     */
    public VWAPBandIndicator(AbstractVWAPIndicator vwapIndicator, Indicator<Num> standardDeviationIndicator,
            Number multiplier, BandType bandType) {
        super(IndicatorSeriesUtils.requireSameSeries(vwapIndicator, standardDeviationIndicator));
        this.vwapIndicator = vwapIndicator;
        this.standardDeviationIndicator = standardDeviationIndicator;
        this.bandType = Objects.requireNonNull(bandType, "bandType must not be null");
        this.multiplier = getBarSeries().numFactory()
                .numOf(Objects.requireNonNull(multiplier, "multiplier must not be null"));
        Indicator<Num> multiplierIndicator = new ConstantIndicator<>(getBarSeries(), this.multiplier);
        this.scaledDeviation = BinaryOperationIndicator.product(standardDeviationIndicator, multiplierIndicator);
        this.band = bandType == BandType.UPPER ? BinaryOperationIndicator.sum(vwapIndicator, scaledDeviation)
                : BinaryOperationIndicator.difference(vwapIndicator, scaledDeviation);
    }

    @Override
    protected Num calculate(int index) {
        Num vwap = vwapIndicator.getValue(index);
        Num deviation = standardDeviationIndicator.getValue(index);
        if (isInvalid(vwap) || isInvalid(deviation)) {
            return NaN.NaN;
        }
        return band.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(vwapIndicator.getCountOfUnstableBars(), standardDeviationIndicator.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " bandType: " + bandType;
    }

    private static boolean isInvalid(Num value) {
        if (Num.isNaNOrNull(value)) {
            return true;
        }
        return Double.isNaN(value.doubleValue());
    }
}
