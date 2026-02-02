/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottProjectionIndicatorTest {

    private final NumFactory numFactory = DecimalNumFactory.getInstance();

    @Test
    void returnsNaNWhenNoScenarios() {
        BarSeries series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).volume(0).add();

        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottProjectionIndicator projectionIndicator = new ElliottProjectionIndicator(scenarioIndicator);

        Num projection = projectionIndicator.getValue(0);

        // With no scenarios, projection returns NaN - check via the Num API
        assertThat(Num.isNaNOrNull(projection)).isTrue();
    }

    @Test
    void allTargetsFromPrimaryScenario() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottProjectionIndicator projectionIndicator = new ElliottProjectionIndicator(scenarioIndicator);

        List<Num> targets = projectionIndicator.allTargets(series.getEndIndex());

        // List should never be null (returns empty list if no scenario)
        assertThat(targets).isNotNull();

        // Check if primary scenario exists - if it does, targets may be present
        var primaryScenario = scenarioIndicator.primaryScenario(series.getEndIndex());
        if (primaryScenario.isPresent()) {
            // If scenario exists, targets list should match scenario's fibonacciTargets
            // exactly
            List<Num> scenarioTargets = primaryScenario.get().fibonacciTargets();
            assertThat(targets).hasSize(scenarioTargets.size());
            assertThat(targets).containsExactlyElementsOf(scenarioTargets);

            // If targets are present, they should be valid values
            if (!targets.isEmpty()) {
                assertThat(targets).allMatch(t -> Num.isValid(t), "All targets should be valid");
            }
        } else {
            // If no primary scenario, targets should be empty
            assertThat(targets).isEmpty();
        }
    }

    @Test
    void calculateWave3Target() {
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.MINOR), // Wave 1:
                                                                                                           // +20
                new ElliottSwing(5, 10, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.MINOR)); // Wave 2
                                                                                                             // end at
                                                                                                             // 110

        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottProjectionIndicator projectionIndicator = new ElliottProjectionIndicator(scenarioIndicator);

        List<Num> targets = projectionIndicator.calculateTargets(swings, ElliottPhase.WAVE2);

        // Should have wave 3 targets: 1.618, 2.618, 1.0 extensions from wave 2 end
        assertThat(targets).isNotEmpty();

        // Wave 1 amplitude = 20, 1.618 extension from 110 = 110 + (20 * 1.618) = 142.36
        boolean has1618Target = targets.stream().anyMatch(t -> Math.abs(t.doubleValue() - 142.36) < 1.0);
        assertThat(has1618Target).isTrue();
    }

    @Test
    void calculateCorrectiveTargets() {
        // A-B-C corrective pattern: Wave A rises 100 -> 130 (amplitude = 30),
        // Wave B retraces to 115, Wave C moves opposite to A (downward).
        // According to Elliott Wave theory, Wave C typically equals Wave A amplitude.
        // From Wave B end (115), Wave C target = 115 - 30 = 85
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 5, numFactory.numOf(100), numFactory.numOf(130), ElliottDegree.MINOR), // Wave A:
                                                                                                           // +30
                new ElliottSwing(5, 10, numFactory.numOf(130), numFactory.numOf(115), ElliottDegree.MINOR)); // Wave B
                                                                                                             // end at
                                                                                                             // 115

        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottProjectionIndicator projectionIndicator = new ElliottProjectionIndicator(scenarioIndicator);

        List<Num> targets = projectionIndicator.calculateTargets(swings, ElliottPhase.CORRECTIVE_B);

        // Should have wave C targets: 1.0 equality, 1.618 extension, 0.618 truncated
        assertThat(targets).isNotEmpty();

        // Primary target: Wave C equals Wave A amplitude (A = C equality)
        // Wave B ends at 115, Wave A amplitude is 30, Wave C moves down: 115 - 30 = 85
        boolean hasEqualityTarget = targets.stream().anyMatch(t -> Math.abs(t.doubleValue() - 85.0) < 1.0);
        assertThat(hasEqualityTarget).isTrue();
    }

    @Test
    void unstableBarsFromScenarioIndicator() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 3, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottProjectionIndicator projectionIndicator = new ElliottProjectionIndicator(scenarioIndicator);

        assertThat(projectionIndicator.getCountOfUnstableBars()).isEqualTo(scenarioIndicator.getCountOfUnstableBars());
    }

    private BarSeries createSeriesWithSwings() {
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder();
        BarSeries series = builder.build();

        double[] prices = { 100, 110, 105, 120, 108, 130, 115, 140, 125, 150 };
        for (double price : prices) {
            series.barBuilder()
                    .openPrice(price - 2)
                    .highPrice(price + 2)
                    .lowPrice(price - 3)
                    .closePrice(price)
                    .volume(1000)
                    .add();
        }

        return series;
    }
}
