/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.Objects;
import java.util.function.BinaryOperator;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.Num;

/**
 * Combine indicator.
 *
 * <p>
 * Combines two Num indicators by using common math operations.
 *
 * @deprecated Migrate usage to equivalent functions in
 *             BinaryOperationIndicator. This class will be deleted in an
 *             upcoming release
 */
@Deprecated
public class CombineIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicatorLeft;
    private final Indicator<Num> indicatorRight;
    private final BinaryOperator<Num> combineFunction;

    /**
     * Combines two indicators backed by the same bar series and its numeric
     * factory.
     *
     * @param indicatorLeft  the indicator for the left hand side of the calculation
     * @param indicatorRight the indicator for the right hand side of the
     *                       calculation
     * @param combination    a {@link BinaryOperator} describing the combination to
     *                       combine the values of the indicators
     * @throws NullPointerException     if an indicator, its series, or the operator
     *                                  is null
     * @throws IllegalArgumentException if the indicators refer to different bar
     *                                  series
     */
    @Deprecated
    public CombineIndicator(Indicator<Num> indicatorLeft, Indicator<Num> indicatorRight,
            BinaryOperator<Num> combination) {
        Objects.requireNonNull(combination, "combination");
        super(IndicatorUtils.requireSameSeries(indicatorLeft, indicatorRight), indicatorLeft, indicatorRight);
        this.indicatorLeft = indicatorLeft;
        this.indicatorRight = indicatorRight;
        this.combineFunction = combination;
    }

    @Override
    @Deprecated
    protected Num calculate(int index) {
        return combineFunction.apply(indicatorLeft.getValue(index), indicatorRight.getValue(index));
    }

    @Override
    @Deprecated
    public int getCountOfUnstableBars() {
        return Math.max(indicatorLeft.getCountOfUnstableBars(), indicatorRight.getCountOfUnstableBars());
    }

    /**
     * Combines the two input indicators by indicatorLeft.plus(indicatorRight).
     */
    @Deprecated
    public static CombineIndicator plus(Indicator<Num> indicatorLeft, Indicator<Num> indicatorRight) {
        return new CombineIndicator(indicatorLeft, indicatorRight, Num::plus);
    }

    /**
     * Combines the two input indicators by indicatorLeft.minus(indicatorRight).
     */
    @Deprecated
    public static CombineIndicator minus(Indicator<Num> indicatorLeft, Indicator<Num> indicatorRight) {
        return new CombineIndicator(indicatorLeft, indicatorRight, Num::minus);
    }

    /**
     * Combines the two input indicators by indicatorLeft.dividedBy(indicatorRight).
     */
    @Deprecated
    public static CombineIndicator divide(Indicator<Num> indicatorLeft, Indicator<Num> indicatorRight) {
        return new CombineIndicator(indicatorLeft, indicatorRight, Num::dividedBy);
    }

    /**
     * Combines the two input indicators by
     * indicatorLeft.multipliedBy(indicatorRight).
     */
    @Deprecated
    public static CombineIndicator multiply(Indicator<Num> indicatorLeft, Indicator<Num> indicatorRight) {
        return new CombineIndicator(indicatorLeft, indicatorRight, Num::multipliedBy);
    }

    /**
     * Combines the two input indicators by indicatorLeft.max(indicatorRight).
     */
    @Deprecated
    public static CombineIndicator max(Indicator<Num> indicatorLeft, Indicator<Num> indicatorRight) {
        return new CombineIndicator(indicatorLeft, indicatorRight, Num::max);
    }

    /**
     * Combines the two input indicators by indicatorLeft.min(indicatorRight).
     */
    @Deprecated
    public static CombineIndicator min(Indicator<Num> indicatorLeft, Indicator<Num> indicatorRight) {
        return new CombineIndicator(indicatorLeft, indicatorRight, Num::min);
    }

    @Override
    @Deprecated
    public String toString() {
        return getClass().getSimpleName();
    }
}
