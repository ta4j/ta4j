/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator.SimpleLinearRegressionType;
import org.ta4j.core.num.Num;

/**
 * Rolling slope of an edge indicator.
 *
 * <p>
 * Positive values indicate the supplied edge indicator is improving over the
 * lookback window, while negative values indicate decay.
 * </p>
 *
 * @since 0.22.7
 */
public class EdgeDecaySlopeIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> edgeIndicator;
    private final SimpleLinearRegressionIndicator slopeIndicator;
    private final int barCount;

    /**
     * Creates a slope indicator over the supplied edge series.
     *
     * @param edgeIndicator the edge indicator to analyze
     * @param barCount      the regression lookback
     * @since 0.22.7
     */
    public EdgeDecaySlopeIndicator(Indicator<Num> edgeIndicator, int barCount) {
        this(validatedConfig(edgeIndicator, barCount));
    }

    private EdgeDecaySlopeIndicator(Config config) {
        super(config.edgeIndicator());
        this.edgeIndicator = config.edgeIndicator();
        this.barCount = config.barCount();
        this.slopeIndicator = config.slopeIndicator();
    }

    private static Config validatedConfig(Indicator<Num> edgeIndicator, int barCount) {
        Indicator<Num> validatedEdgeIndicator = Objects.requireNonNull(edgeIndicator, "edgeIndicator");
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be at least two");
        }
        SimpleLinearRegressionIndicator slopeIndicator = new SimpleLinearRegressionIndicator(validatedEdgeIndicator,
                barCount, SimpleLinearRegressionType.SLOPE);
        return new Config(validatedEdgeIndicator, slopeIndicator, barCount);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    protected Num calculate(int index) {
        return slopeIndicator.getValue(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public int getCountOfUnstableBars() {
        return slopeIndicator.getCountOfUnstableBars();
    }

    /**
     * @return the edge indicator being analyzed
     * @since 0.22.7
     */
    public Indicator<Num> getEdgeIndicator() {
        return edgeIndicator;
    }

    /**
     * @return the regression lookback
     * @since 0.22.7
     */
    public int getBarCount() {
        return barCount;
    }

    private record Config(Indicator<Num> edgeIndicator, SimpleLinearRegressionIndicator slopeIndicator, int barCount) {
    }
}
