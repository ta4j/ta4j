/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.builder;

import org.ta4j.core.BarSeries;

import ta4jexamples.charting.builder.ChartBuilder.ChartDefinitionMetadata;

/**
 * Immutable plan describing a chart composition along with shared metadata.
 */
public final class ChartPlan {

    private final ChartContext context;

    ChartPlan(ChartBuilder.ChartDefinition definition) {
        this.context = ChartContext.from(definition);
    }

    public ChartBuilder.ChartDefinition definition() {
        return context.definition();
    }

    /**
     * Returns the metadata for this chart plan.
     *
     * @return the chart metadata
     * @since 0.23
     */
    public ChartDefinitionMetadata metadata() {
        return context.metadata();
    }

    /**
     * Returns the chart context containing definition and metadata.
     *
     * @return the chart context
     * @since 0.23
     */
    public ChartContext context() {
        return context;
    }

    /**
     * Returns the primary domain series for this chart plan.
     *
     * @return the primary series
     */
    public BarSeries primarySeries() {
        return context.domainSeries();
    }
}
