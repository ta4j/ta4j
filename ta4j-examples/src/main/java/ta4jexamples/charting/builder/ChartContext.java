/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.builder;

import java.util.Objects;

import org.ta4j.core.BarSeries;

/**
 * Immutable context describing a chart definition alongside its shared
 * metadata.
 *
 * @since 0.23
 */
public record ChartContext(ChartBuilder.ChartDefinition definition, ChartBuilder.ChartDefinitionMetadata metadata) {

    public ChartContext {
        Objects.requireNonNull(definition, "Chart definition cannot be null");
        Objects.requireNonNull(metadata, "Chart metadata cannot be null");
    }

    public static ChartContext from(ChartBuilder.ChartDefinition definition) {
        Objects.requireNonNull(definition, "Chart definition cannot be null");
        return new ChartContext(definition, definition.metadata());
    }

    public BarSeries domainSeries() {
        return metadata.domainSeries();
    }

    public String title() {
        return metadata.title();
    }

    public TimeAxisMode timeAxisMode() {
        return metadata.timeAxisMode();
    }
}
