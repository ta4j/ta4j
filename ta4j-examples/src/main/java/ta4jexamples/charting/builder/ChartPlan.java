/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package ta4jexamples.charting.builder;

import org.ta4j.core.BarSeries;

import java.util.Objects;

/**
 * Immutable plan describing a chart composition along with the originating
 * series.
 */
public final class ChartPlan {

    private final ChartBuilder.ChartDefinition definition;
    private final BarSeries primarySeries;

    ChartPlan(ChartBuilder.ChartDefinition definition, BarSeries primarySeries) {
        this.definition = Objects.requireNonNull(definition, "Chart definition cannot be null");
        this.primarySeries = Objects.requireNonNull(primarySeries, "Primary series cannot be null");
    }

    public ChartBuilder.ChartDefinition definition() {
        return definition;
    }

    public BarSeries primarySeries() {
        return primarySeries;
    }
}
