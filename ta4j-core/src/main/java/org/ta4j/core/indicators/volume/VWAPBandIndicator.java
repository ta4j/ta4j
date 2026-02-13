/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.BandIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

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
    private final transient BandIndicator band;
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
        if (Num.isNaNOrNull(this.multiplier)) {
            throw new IllegalArgumentException("multiplier must be a valid number");
        }
        this.band = new BandIndicator(vwapIndicator, standardDeviationIndicator, this.multiplier.getDelegate(),
                BandIndicator.BandType.valueOf(bandType.name()));
    }

    /**
     * Convenience constructor using {@link VWAPStandardDeviationIndicator} for the
     * supplied VWAP definition.
     *
     * @param vwapIndicator the VWAP indicator
     * @param multiplier    number of standard deviations to offset
     * @param bandType      upper or lower band selection
     * @since 0.22.2
     */
    public VWAPBandIndicator(AbstractVWAPIndicator vwapIndicator, Number multiplier, BandType bandType) {
        this(vwapIndicator, new VWAPStandardDeviationIndicator(vwapIndicator), multiplier, bandType);
    }

    /**
     * Calculates the indicator value at the requested index.
     */
    @Override
    protected Num calculate(int index) {
        return band.getValue(index);
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(vwapIndicator.getCountOfUnstableBars(), standardDeviationIndicator.getCountOfUnstableBars());
    }

    /**
     * Implements to descriptor.
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("multiplier", normalizeMultiplier(multiplier));
        parameters.put("bandType", bandType.name());
        return ComponentDescriptor.builder()
                .withType(getClass().getSimpleName())
                .withParameters(parameters)
                .addComponent(vwapIndicator.toDescriptor())
                .addComponent(standardDeviationIndicator.toDescriptor())
                .build();
    }

    /**
     * Returns the JSON representation of this component.
     */
    @Override
    public String toJson() {
        return ComponentSerialization.toJson(toDescriptor());
    }

    /**
     * Returns a string representation of this component.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " bandType: " + bandType;
    }

    /**
     * Implements normalize multiplier.
     */
    private static String normalizeMultiplier(Num value) {
        if (value == null) {
            return null;
        }
        String raw = value.getDelegate().toString();
        try {
            BigDecimal decimal = new BigDecimal(raw).stripTrailingZeros();
            if (decimal.scale() < 0) {
                decimal = decimal.setScale(0);
            }
            return decimal.toPlainString();
        } catch (NumberFormatException ex) {
            return raw;
        }
    }
}
