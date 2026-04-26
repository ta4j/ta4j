/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.named;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Test fixture for {@link NamedRule}.
 */
public final class NamedRuleFixture extends NamedRule {

    static {
        registerImplementation(NamedRuleFixture.class);
    }

    private final ClosePriceIndicator closePriceIndicator;
    private final Num threshold;
    private final Comparison comparison;

    public NamedRuleFixture(ClosePriceIndicator closePriceIndicator, Num threshold, Comparison comparison) {
        super(buildLabel(comparison, threshold));
        this.closePriceIndicator = closePriceIndicator;
        this.threshold = threshold;
        this.comparison = comparison;
    }

    public NamedRuleFixture(BarSeries series, Comparison comparison, Num threshold) {
        this(new ClosePriceIndicator(series), threshold, comparison);
    }

    public NamedRuleFixture(BarSeries series, String... parameters) {
        this(series, parseComparison(parameters), parseThreshold(series, parameters));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        Num price = closePriceIndicator.getValue(index);
        boolean satisfied = comparison == Comparison.ABOVE ? price.isGreaterThan(threshold)
                : price.isLessThan(threshold);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    public Num getThreshold() {
        return threshold;
    }

    public Comparison getComparison() {
        return comparison;
    }

    private static Comparison parseComparison(String... parameters) {
        if (parameters == null || parameters.length != 2) {
            throw new IllegalArgumentException("NamedRuleFixture expects [comparison, threshold]");
        }
        return Comparison.valueOf(parameters[0]);
    }

    private static Num parseThreshold(BarSeries series, String... parameters) {
        if (parameters == null || parameters.length != 2) {
            throw new IllegalArgumentException("NamedRuleFixture expects [comparison, threshold]");
        }
        return series.numFactory().numOf(parameters[1]);
    }

    private static String buildLabel(Comparison comparison, Num threshold) {
        return NamedRule.buildLabel(NamedRuleFixture.class, comparison.name(), threshold.toString());
    }

    enum Comparison {
        ABOVE, BELOW
    }
}
