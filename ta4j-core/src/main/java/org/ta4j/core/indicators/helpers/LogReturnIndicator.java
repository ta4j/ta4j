/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Log return of a numeric source indicator.
 *
 * <p>
 * For a source value {@code x}, this indicator returns
 * {@code log(x[i] / x[i - barCount])}. Values are undefined during warm-up or
 * when either side of the ratio is non-positive or invalid.
 *
 * @since 0.22.9
 */
public final class LogReturnIndicator extends CachedIndicator<Num> implements ReturnIndicator {

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor using close prices and a one-bar return.
     *
     * @param series bar series
     * @since 0.22.9
     */
    public LogReturnIndicator(BarSeries series) {
        this(config(new ClosePriceIndicator(Objects.requireNonNull(series, "series must not be null")), 1));
    }

    /**
     * Constructor using a one-bar return.
     *
     * @param indicator source indicator
     * @since 0.22.9
     */
    public LogReturnIndicator(Indicator<Num> indicator) {
        this(config(indicator, 1));
    }

    /**
     * Constructor.
     *
     * @param indicator source indicator
     * @param barCount  distance between current and previous source values
     * @since 0.22.9
     */
    public LogReturnIndicator(Indicator<Num> indicator, int barCount) {
        this(config(indicator, barCount));
    }

    private LogReturnIndicator(Config config) {
        super(config.indicator());
        this.indicator = config.indicator();
        this.barCount = config.barCount();
    }

    /**
     * Returns the configured lookback distance.
     *
     * @return bar count
     * @since 0.22.9
     */
    public int getBarCount() {
        return barCount;
    }

    /**
     * Returns the source indicator used to calculate log returns.
     *
     * @return source indicator
     * @since 0.22.9
     */
    public Indicator<Num> getSourceIndicator() {
        return indicator;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public ReturnRepresentation getReturnRepresentation() {
        return ReturnRepresentation.LOG;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num current = indicator.getValue(index);
        Num previous = indicator.getValue(index - barCount);
        if (IndicatorUtils.isInvalid(current) || IndicatorUtils.isInvalid(previous) || !current.isPositive()
                || !previous.isPositive()) {
            return NaN.NaN;
        }
        Num result = current.dividedBy(previous).log();
        if (IndicatorUtils.isInvalid(result)) {
            return NaN.NaN;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount;
    }

    private static Config config(Indicator<Num> indicator, int barCount) {
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1");
        }
        return new Config(Objects.requireNonNull(indicator, "indicator must not be null"), barCount);
    }

    private record Config(Indicator<Num> indicator, int barCount) {
    }
}
