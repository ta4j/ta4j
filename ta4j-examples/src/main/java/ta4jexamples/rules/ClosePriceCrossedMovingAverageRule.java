/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.named.NamedRule;

import java.util.Objects;

/**
 * Named close-price crossover rule against an SMA or EMA.
 *
 * <p>
 * Labels follow
 * {@code ClosePriceCrossedMovingAverageRule_<UP|DOWN>_<SMA|EMA>_<period>}.
 * </p>
 *
 * @since 0.22.7
 */
public class ClosePriceCrossedMovingAverageRule extends NamedRule {

    private final ClosePriceIndicator closePriceIndicator;
    private final int period;
    private final AverageType averageType;
    private final CrossDirection direction;
    private transient Indicator<Num> movingAverageIndicator;
    private transient Rule delegateRule;

    /**
     * Creates a crossover rule from reconstruction-friendly components.
     *
     * @param closePriceIndicator close-price indicator
     * @param period              moving-average period
     * @param averageType         average family
     * @param direction           crossover direction
     * @since 0.22.7
     */
    public ClosePriceCrossedMovingAverageRule(ClosePriceIndicator closePriceIndicator, int period,
            AverageType averageType, CrossDirection direction) {
        super(buildLabel(direction, averageType, period));
        this.closePriceIndicator = Objects.requireNonNull(closePriceIndicator, "closePriceIndicator");
        this.period = validatePeriod(period);
        this.averageType = Objects.requireNonNull(averageType, "averageType");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    /**
     * Creates a crossover rule from strongly typed parameters.
     *
     * @param series      backing bar series
     * @param averageType average family
     * @param direction   crossover direction
     * @param period      moving-average period
     * @since 0.22.7
     */
    public ClosePriceCrossedMovingAverageRule(BarSeries series, AverageType averageType, CrossDirection direction,
            int period) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")), period, averageType, direction);
    }

    /**
     * Reconstructs the rule from compact label parameters.
     *
     * @param series backing bar series
     * @param params compact label parameters
     * @since 0.22.7
     */
    public ClosePriceCrossedMovingAverageRule(BarSeries series, String... params) {
        this(series, parseAverageType(params), parseDirection(params), parsePeriod(params));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        return getDelegateRule().isSatisfied(index, tradingRecord);
    }

    private Rule getDelegateRule() {
        if (delegateRule == null) {
            Indicator<Num> movingAverage = getMovingAverageIndicator();
            delegateRule = direction == CrossDirection.UP
                    ? new CrossedUpIndicatorRule(closePriceIndicator, movingAverage)
                    : new CrossedDownIndicatorRule(closePriceIndicator, movingAverage);
        }
        return delegateRule;
    }

    private Indicator<Num> getMovingAverageIndicator() {
        if (movingAverageIndicator == null) {
            movingAverageIndicator = averageType == AverageType.EMA ? new EMAIndicator(closePriceIndicator, period)
                    : new SMAIndicator(closePriceIndicator, period);
        }
        return movingAverageIndicator;
    }

    private static String buildLabel(CrossDirection direction, AverageType averageType, int period) {
        return NamedRule.buildLabel(ClosePriceCrossedMovingAverageRule.class, direction.name(), averageType.name(),
                String.valueOf(period));
    }

    private static AverageType parseAverageType(String... params) {
        validateParams(params);
        try {
            return AverageType.valueOf(params[1]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("ClosePriceCrossedMovingAverageRule average type must be SMA or EMA.",
                    ex);
        }
    }

    private static CrossDirection parseDirection(String... params) {
        validateParams(params);
        try {
            return CrossDirection.valueOf(params[0]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("ClosePriceCrossedMovingAverageRule direction must be UP or DOWN.", ex);
        }
    }

    private static int parsePeriod(String... params) {
        validateParams(params);
        try {
            int period = Integer.parseInt(params[2]);
            return validatePeriod(period);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("ClosePriceCrossedMovingAverageRule period must be an integer.", ex);
        }
    }

    private static int validatePeriod(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("ClosePriceCrossedMovingAverageRule period must be greater than zero.");
        }
        return period;
    }

    private static void validateParams(String... params) {
        if (params == null || params.length != 3) {
            throw new IllegalArgumentException(
                    "ClosePriceCrossedMovingAverageRule expects [direction, averageType, period].");
        }
    }

    /**
     * Moving-average family used for the crossover.
     *
     * @since 0.22.7
     */
    public enum AverageType {
        SMA, EMA
    }

    /**
     * Direction used for the crossover test.
     *
     * @since 0.22.7
     */
    public enum CrossDirection {
        UP, DOWN
    }
}
