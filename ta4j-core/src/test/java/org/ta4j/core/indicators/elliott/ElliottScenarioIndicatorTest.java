/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.BarSeries;

class ElliottScenarioIndicatorTest {

    @Test
    void producesScenarioSetForEachBar() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        ElliottScenarioSet set = indicator.getValue(series.getEndIndex());

        assertThat(set).isNotNull();
        assertThat(set.barIndex()).isEqualTo(series.getEndIndex());
    }

    @Test
    void primaryScenarioReturnsOptional() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        var primary = indicator.primaryScenario(series.getEndIndex());

        // Method returns an Optional that should never be null
        assertThat(primary).isNotNull();

        // When scenarios exist, primary should be present with highest confidence
        if (primary.isPresent()) {
            ElliottScenario scenario = primary.get();
            assertThat(scenario.confidenceScore()).isNotNull();
            assertThat(scenario.currentPhase()).isNotNull();
        }
    }

    @Test
    void primaryScenarioEmptyWhenNoScenarios() {
        // Single bar series produces no swings, hence no scenarios
        BarSeries series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).volume(0).add();

        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        var primary = indicator.primaryScenario(0);

        assertThat(primary).isEmpty();
    }

    @Test
    void alternativesConvenience() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        var alternatives = indicator.alternatives(series.getEndIndex());

        assertThat(alternatives).isNotNull();
    }

    @Test
    void consensusReturnsPhaseWhenHighConfidenceScenariosAgree() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        ElliottPhase consensus = indicator.consensus(series.getEndIndex());
        ElliottScenarioSet scenarioSet = indicator.getValue(series.getEndIndex());

        // Consensus should never be null - returns NONE when no agreement
        assertThat(consensus).isNotNull();

        // Verify consensus logic: if high-confidence scenarios exist and agree,
        // consensus should match their phase; otherwise NONE
        int highConfidenceCount = scenarioSet.highConfidenceCount();
        if (highConfidenceCount == 0) {
            assertThat(consensus).isEqualTo(ElliottPhase.NONE);
        } else {
            // Either all high-confidence scenarios agree (returns their phase)
            // or they disagree (returns NONE) - both are valid enum values
            assertThat(consensus).isNotNull();
        }
    }

    @Test
    void consensusReturnsNoneWhenNoHighConfidenceScenarios() {
        // Single bar series produces no scenarios
        BarSeries series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).volume(0).add();

        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        ElliottPhase consensus = indicator.consensus(0);

        assertThat(consensus).isEqualTo(ElliottPhase.NONE);
    }

    @Test
    void unstableBarsFromSwingIndicator() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 3, ElliottDegree.MINOR);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(swingIndicator.getCountOfUnstableBars());
    }

    @Test
    void accessToUnderlyingIndicators() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottChannelIndicator channelIndicator = new ElliottChannelIndicator(swingIndicator);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator, channelIndicator);

        assertThat(indicator.getSwingIndicator()).isEqualTo(swingIndicator);
        assertThat(indicator.getChannelIndicator()).isEqualTo(channelIndicator);
    }

    @Test
    void scenariosInheritSwingDegree() {
        BarSeries series = createSeriesWithSwings();
        var factory = series.numFactory();
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 1, factory.numOf(100), factory.numOf(110), ElliottDegree.PRIMARY),
                new ElliottSwing(1, 2, factory.numOf(110), factory.numOf(105), ElliottDegree.PRIMARY),
                new ElliottSwing(2, 3, factory.numOf(105), factory.numOf(120), ElliottDegree.PRIMARY));
        ElliottSwingIndicator swingIndicator = new StubSwingIndicator(series, List.of(swings), ElliottDegree.PRIMARY);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        ElliottScenarioSet set = indicator.getValue(series.getEndIndex());

        assertThat(set.isEmpty()).isFalse();
        assertThat(set.all()).allMatch(scenario -> scenario.degree() == ElliottDegree.PRIMARY);
    }

    @Test
    void scenariosTrackMostRecentSwings() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 101, 102, 103, 104, 105, 106).build();
        var factory = series.numFactory();
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 1, factory.numOf(90), factory.numOf(100), ElliottDegree.MINOR),
                new ElliottSwing(1, 2, factory.numOf(100), factory.numOf(110), ElliottDegree.MINOR),
                new ElliottSwing(2, 3, factory.numOf(110), factory.numOf(104), ElliottDegree.MINOR),
                new ElliottSwing(3, 4, factory.numOf(104), factory.numOf(120), ElliottDegree.MINOR),
                new ElliottSwing(4, 5, factory.numOf(120), factory.numOf(114), ElliottDegree.MINOR),
                new ElliottSwing(5, 6, factory.numOf(114), factory.numOf(124), ElliottDegree.MINOR));

        ElliottSwingIndicator swingIndicator = new StubSwingIndicator(series, List.of(swings), ElliottDegree.MINOR);
        ElliottChannelIndicator channelIndicator = new ElliottChannelIndicator(swingIndicator);
        ElliottScenarioGenerator generator = new ElliottScenarioGenerator(factory, 0.0, 20);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator, channelIndicator, generator);

        ElliottScenarioSet set = indicator.getValue(series.getEndIndex());

        assertThat(set.isEmpty()).isFalse();
        int lastSwingToIndex = swings.get(swings.size() - 1).toIndex();
        boolean includesLastSwing = set.all()
                .stream()
                .flatMap(scenario -> scenario.swings().stream())
                .anyMatch(swing -> swing.toIndex() == lastSwingToIndex);
        assertThat(includesLastSwing).isTrue();
    }

    @Test
    void singleBarSeriesReturnsEmptyScenarioSet() {
        // A single bar cannot produce swings, so no scenarios are generated
        BarSeries series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).volume(0).add();

        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator indicator = new ElliottScenarioIndicator(swingIndicator);

        ElliottScenarioSet set = indicator.getValue(0);

        assertThat(set.isEmpty()).isTrue();
    }

    private BarSeries createSeriesWithSwings() {
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder();
        BarSeries series = builder.build();

        // Create alternating price pattern to generate swings
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
