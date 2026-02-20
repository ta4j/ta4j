/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.math.BigDecimal;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Classifies MACD-V values into momentum states.
 *
 * <p>
 * During the unstable window inherited from the source MACD-V indicator, this
 * indicator returns {@link MACDVMomentumState#UNDEFINED}.
 *
 * @since 0.22.2
 */
public class MACDVMomentumStateIndicator extends CachedIndicator<MACDVMomentumState> {

    private final Indicator<Num> macdVIndicator;
    private final MACDVMomentumProfile momentumProfile;
    private final BigDecimal positiveRangeThreshold;
    private final BigDecimal positiveRiskThreshold;
    private final BigDecimal negativeRangeThreshold;
    private final BigDecimal negativeRiskThreshold;

    /**
     * Creates a momentum-state indicator using the default thresholds.
     *
     * @param macdVIndicator MACD-V source indicator
     * @since 0.22.2
     */
    public MACDVMomentumStateIndicator(Indicator<Num> macdVIndicator) {
        this(macdVIndicator, MACDVMomentumProfile.defaultProfile());
    }

    /**
     * Creates a momentum-state indicator using a custom profile.
     *
     * @param macdVIndicator  MACD-V source indicator
     * @param momentumProfile momentum profile
     * @since 0.22.2
     */
    public MACDVMomentumStateIndicator(Indicator<Num> macdVIndicator, MACDVMomentumProfile momentumProfile) {
        this(macdVIndicator, Objects.requireNonNull(momentumProfile, "momentumProfile").positiveRangeThreshold(),
                momentumProfile.positiveRiskThreshold(), momentumProfile.negativeRangeThreshold(),
                momentumProfile.negativeRiskThreshold());
    }

    /**
     * Creates a momentum-state indicator with explicit threshold values.
     *
     * @param macdVIndicator         MACD-V source indicator
     * @param positiveRangeThreshold lower bound of positive momentum range
     * @param positiveRiskThreshold  lower bound of positive risk range
     * @param negativeRangeThreshold upper bound of negative momentum range
     * @param negativeRiskThreshold  upper bound of negative risk range
     * @since 0.22.2
     */
    public MACDVMomentumStateIndicator(Indicator<Num> macdVIndicator, Number positiveRangeThreshold,
            Number positiveRiskThreshold, Number negativeRangeThreshold, Number negativeRiskThreshold) {
        super(Objects.requireNonNull(macdVIndicator, "macdVIndicator"));
        this.macdVIndicator = macdVIndicator;
        this.momentumProfile = new MACDVMomentumProfile(positiveRangeThreshold, positiveRiskThreshold,
                negativeRangeThreshold, negativeRiskThreshold);
        this.positiveRangeThreshold = toDecimal(momentumProfile.positiveRangeThreshold());
        this.positiveRiskThreshold = toDecimal(momentumProfile.positiveRiskThreshold());
        this.negativeRangeThreshold = toDecimal(momentumProfile.negativeRangeThreshold());
        this.negativeRiskThreshold = toDecimal(momentumProfile.negativeRiskThreshold());
    }

    private static BigDecimal toDecimal(Number threshold) {
        return new BigDecimal(threshold.toString());
    }

    @Override
    protected MACDVMomentumState calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return MACDVMomentumState.UNDEFINED;
        }
        return momentumProfile.classify(macdVIndicator.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return macdVIndicator.getCountOfUnstableBars();
    }

    /**
     * @return source MACD-V indicator
     * @since 0.22.2
     */
    public Indicator<Num> getMacdVIndicator() {
        return macdVIndicator;
    }

    /**
     * @return positive momentum-range threshold
     * @since 0.22.2
     */
    public Number getPositiveRangeThreshold() {
        return positiveRangeThreshold;
    }

    /**
     * @return positive risk-range threshold
     * @since 0.22.2
     */
    public Number getPositiveRiskThreshold() {
        return positiveRiskThreshold;
    }

    /**
     * @return negative momentum-range threshold
     * @since 0.22.2
     */
    public Number getNegativeRangeThreshold() {
        return negativeRangeThreshold;
    }

    /**
     * @return negative risk-range threshold
     * @since 0.22.2
     */
    public Number getNegativeRiskThreshold() {
        return negativeRiskThreshold;
    }

    /**
     * @return momentum profile represented by this indicator
     * @since 0.22.2
     */
    public MACDVMomentumProfile getMomentumProfile() {
        return momentumProfile;
    }
}
