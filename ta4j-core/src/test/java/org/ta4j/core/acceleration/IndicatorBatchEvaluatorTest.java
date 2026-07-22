/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class IndicatorBatchEvaluatorTest {

    @Test
    void evaluatesScalarValuesInIndexOrder() {
        BarSeries series = new MockBarSeriesBuilder().withData(3, 5, 8, 13).build();
        Indicator<Num> indicator = new ClosePriceIndicator(series);

        IndicatorBatchResult<Num> result = IndicatorBatchEvaluator.evaluate(indicator, 1, 3, AccelerationConfig.cpu());

        assertThat(result.values()).extracting(IndexedIndicatorValue::index).containsExactly(1, 2, 3);
        assertThat(result.orderedValues()).extracting(Num::doubleValue).containsExactly(5d, 8d, 13d);
        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.CPU_EVALUATED)).isTrue();
        assertThat(result.diagnostics().nativeInitialized()).isFalse();
    }

    @Test
    void returnsImmutableValueList() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2).build();
        Indicator<Num> indicator = new ClosePriceIndicator(series);
        IndicatorBatchResult<Num> result = IndicatorBatchEvaluator.evaluate(indicator, 0, 1, AccelerationConfig.cpu());

        assertThrows(UnsupportedOperationException.class,
                () -> result.values().add(new IndexedIndicatorValue<>(2, series.numFactory().zero())));
    }

    @Test
    void rejectsReversedAndOutOfRangeRequests() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2).build();
        Indicator<Num> indicator = new ClosePriceIndicator(series);

        assertThrows(IllegalArgumentException.class,
                () -> IndicatorBatchEvaluator.evaluate(indicator, 1, 0, AccelerationConfig.cpu()));
        assertThrows(IllegalArgumentException.class,
                () -> IndicatorBatchEvaluator.evaluate(indicator, -1, 1, AccelerationConfig.cpu()));
        assertThrows(IllegalArgumentException.class,
                () -> IndicatorBatchEvaluator.evaluate(indicator, 0, 2, AccelerationConfig.cpu()));
    }

    @Test
    void requiredDeviceModeFailsWithoutOptionalProvider() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2).build();
        Indicator<Num> indicator = new ClosePriceIndicator(series);
        AccelerationConfig config = new AccelerationConfig(AccelerationMode.METAL, true, 0.10d);

        AccelerationException exception = assertThrows(AccelerationException.class,
                () -> IndicatorBatchEvaluator.evaluate(indicator, 0, 1, config));

        assertThat(exception.code()).isEqualTo(AccelerationDiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE);
    }

    @Test
    void detectsSeriesMutationDuringEvaluation() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        Indicator<Num> mutating = new Indicator<>() {
            @Override
            public Num getValue(int index) {
                if (index == 1) {
                    series.addPrice(4);
                }
                return series.getBar(Math.min(index, series.getEndIndex())).getClosePrice();
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }
        };

        AccelerationException exception = assertThrows(AccelerationException.class,
                () -> IndicatorBatchEvaluator.evaluate(mutating, 0, 2, AccelerationConfig.cpu()));

        assertThat(exception.code()).isEqualTo(AccelerationDiagnosticCode.STALE_SNAPSHOT);
    }

    @Test
    void copiesDiagnostics() {
        AccelerationDiagnostics diagnostics = new AccelerationDiagnostics(AccelerationMode.AUTO, AccelerationMode.CPU,
                "cpu", "test", false,
                List.of(AccelerationDiagnostic.of(AccelerationDiagnosticCode.UNSUPPORTED_GRAPH, "fallback")));

        assertThrows(UnsupportedOperationException.class, () -> diagnostics.diagnostics()
                .add(AccelerationDiagnostic.of(AccelerationDiagnosticCode.CPU_EVALUATED, "cpu")));
    }
}
