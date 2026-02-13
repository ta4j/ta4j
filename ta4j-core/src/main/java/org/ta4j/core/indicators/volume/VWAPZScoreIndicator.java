/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.statistics.ZScoreIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

/**
 * Z-score of price relative to VWAP using volume-weighted standard deviation.
 * Uses the same VWAP window/anchor definition as the supplied VWAP indicators.
 *
 * @since 0.22.2
 */
public class VWAPZScoreIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> deviationIndicator;
    private final Indicator<Num> standardDeviationIndicator;
    private transient ZScoreIndicator zScore;

    /**
     * Constructor.
     *
     * @param deviationIndicator         indicator measuring price - VWAP
     * @param standardDeviationIndicator indicator providing the VWAP standard
     *                                   deviation
     *
     * @since 0.22.2
     */
    public VWAPZScoreIndicator(Indicator<Num> deviationIndicator, Indicator<Num> standardDeviationIndicator) {
        super(IndicatorSeriesUtils.requireSameSeries(deviationIndicator, standardDeviationIndicator));
        this.deviationIndicator = Objects.requireNonNull(deviationIndicator, "deviationIndicator must not be null");
        this.standardDeviationIndicator = Objects.requireNonNull(standardDeviationIndicator,
                "standardDeviationIndicator must not be null");
        this.zScore = new ZScoreIndicator(deviationIndicator, standardDeviationIndicator);
    }

    @Override
    protected Num calculate(int index) {
        return zScore().getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(deviationIndicator.getCountOfUnstableBars(),
                standardDeviationIndicator.getCountOfUnstableBars());
    }

    @Override
    public ComponentDescriptor toDescriptor() {
        return ComponentDescriptor.builder()
                .withType(getClass().getSimpleName())
                .addComponent(deviationIndicator.toDescriptor())
                .addComponent(standardDeviationIndicator.toDescriptor())
                .build();
    }

    @Override
    public String toJson() {
        return ComponentSerialization.toJson(toDescriptor());
    }

    private ZScoreIndicator zScore() {
        if (zScore == null) {
            zScore = new ZScoreIndicator(deviationIndicator, standardDeviationIndicator);
        }
        return zScore;
    }
}
