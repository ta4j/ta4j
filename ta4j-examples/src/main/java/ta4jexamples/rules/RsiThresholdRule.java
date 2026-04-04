/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.named.NamedRule;

import java.util.Objects;

/**
 * Named RSI threshold rule for exploratory CLI and example workflows.
 *
 * <p>
 * Labels follow {@code RsiThresholdRule_<ABOVE|BELOW>_<period>_<threshold>}.
 * For example, {@code RsiThresholdRule_BELOW_14_30} is satisfied when RSI(14)
 * drops strictly below {@code 30}.
 * </p>
 *
 * @since 0.22.7
 */
public class RsiThresholdRule extends NamedRule {

    private final ClosePriceIndicator closePriceIndicator;
    private final int period;
    private final Num threshold;
    private final ThresholdDirection direction;
    private transient RSIIndicator rsiIndicator;

    /**
     * Creates a named RSI threshold rule from reconstruction-friendly components.
     *
     * @param closePriceIndicator close-price indicator
     * @param period              RSI period
     * @param threshold           threshold to compare against
     * @param direction           comparison direction
     * @since 0.22.7
     */
    public RsiThresholdRule(ClosePriceIndicator closePriceIndicator, int period, Num threshold,
            ThresholdDirection direction) {
        super(buildLabel(direction, period, threshold));
        this.closePriceIndicator = closePriceIndicator;
        this.period = validatePeriod(period);
        this.threshold = validateThreshold(threshold);
        this.direction = Objects.requireNonNull(direction, "direction");
        this.rsiIndicator = new RSIIndicator(closePriceIndicator, period);
    }

    /**
     * Creates a named RSI threshold rule from strongly typed parameters.
     *
     * @param series    backing bar series
     * @param direction comparison direction
     * @param period    RSI period
     * @param threshold threshold to compare against
     * @since 0.22.7
     */
    public RsiThresholdRule(BarSeries series, ThresholdDirection direction, int period, double threshold) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")), period,
                series.numFactory().numOf(threshold), direction);
    }

    /**
     * Reconstructs the rule from compact label parameters.
     *
     * @param series backing bar series
     * @param params compact label parameters
     * @since 0.22.7
     */
    public RsiThresholdRule(BarSeries series, String... params) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")), parsePeriod(params),
                parseThreshold(series, params), parseDirection(params));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        Num value = getRsiIndicator().getValue(index);
        boolean satisfied = direction == ThresholdDirection.ABOVE ? value.isGreaterThan(threshold)
                : value.isLessThan(threshold);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private RSIIndicator getRsiIndicator() {
        if (rsiIndicator == null) {
            rsiIndicator = new RSIIndicator(closePriceIndicator, period);
        }
        return rsiIndicator;
    }

    private static String buildLabel(ThresholdDirection direction, int period, Num threshold) {
        return NamedRule.buildLabel(RsiThresholdRule.class, direction.name(), String.valueOf(period),
                formatThreshold(threshold));
    }

    private static int validatePeriod(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("RsiThresholdRule period must be greater than zero.");
        }
        return period;
    }

    private static Num validateThreshold(Num threshold) {
        return Objects.requireNonNull(threshold, "threshold");
    }

    private static ThresholdDirection parseDirection(String... params) {
        validateParams(params);
        try {
            return ThresholdDirection.valueOf(params[0]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("RsiThresholdRule direction must be ABOVE or BELOW.", ex);
        }
    }

    private static int parsePeriod(String... params) {
        validateParams(params);
        try {
            int period = Integer.parseInt(params[1]);
            return validatePeriod(period);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("RsiThresholdRule period must be an integer.", ex);
        }
    }

    private static Num parseThreshold(BarSeries series, String... params) {
        validateParams(params);
        return series.numFactory().numOf(params[2]);
    }

    private static String formatThreshold(Num threshold) {
        double value = threshold.doubleValue();
        if (Double.isFinite(value) && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return threshold.toString();
    }

    private static void validateParams(String... params) {
        if (params == null || params.length != 3) {
            throw new IllegalArgumentException("RsiThresholdRule expects [direction, period, threshold].");
        }
    }

    /**
     * Direction used for threshold comparison.
     *
     * @since 0.22.7
     */
    public enum ThresholdDirection {
        ABOVE, BELOW
    }
}
