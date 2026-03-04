/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WalkForwardHoldoutValidatorTest {

    @Test
    void validateReturnsFailureWhenHoldoutMetricsAreUnavailable() {
        WalkForwardRunResult<String, Boolean> runResult = runResult(Map.of());
        WalkForwardHoldoutValidator validator = new WalkForwardHoldoutValidator();

        WalkForwardHoldoutValidator.Report report = validator.validate(runResult, "c-1", Map.of("ece", 0.2), Map.of());

        assertThat(report.passed()).isFalse();
        assertThat(report.metricValues()).isEmpty();
        assertThat(report.notes()).contains("holdout metrics unavailable");
    }

    @Test
    void validateAppliesMinimumAndMaximumGuardrails() {
        Map<Integer, Map<String, Map<String, Double>>> foldMetrics = Map.of(10,
                Map.of("holdout", Map.of("agreement", 0.5, "brier", 0.4)));
        WalkForwardRunResult<String, Boolean> runResult = runResult(foldMetrics);
        WalkForwardHoldoutValidator validator = new WalkForwardHoldoutValidator();

        WalkForwardHoldoutValidator.Report report = validator.validate(runResult, "c-2", Map.of("agreement", 0.8),
                Map.of("brier", 0.2));

        assertThat(report.passed()).isFalse();
        assertThat(report.notes()).contains("agreement < 0.8", "brier > 0.2");
    }

    @Test
    void validateMarksPassingReportWhenAllGuardrailsPass() {
        Map<Integer, Map<String, Map<String, Double>>> foldMetrics = Map.of(10,
                Map.of("holdout", Map.of("agreement", 0.9, "brier", 0.1)));
        WalkForwardRunResult<String, Boolean> runResult = runResult(foldMetrics);
        WalkForwardHoldoutValidator validator = new WalkForwardHoldoutValidator();

        WalkForwardHoldoutValidator.Report report = validator.validate(runResult, "c-3", Map.of("agreement", 0.8),
                Map.of("brier", 0.2));

        assertThat(report.passed()).isTrue();
        assertThat(report.notes()).contains("holdout metrics satisfied all guardrails");
    }

    private static WalkForwardRunResult<String, Boolean> runResult(
            Map<Integer, Map<String, Map<String, Double>>> foldMetricsByHorizon) {
        WalkForwardConfig config = new WalkForwardConfig(20, 10, 10, 0, 0, 5, 10, List.of(), 1, List.of(), 42L);
        WalkForwardExperimentManifest manifest = new WalkForwardExperimentManifest("dataset", "candidate",
                config.configHash(), config.seed(), Map.of());
        return new WalkForwardRunResult<>(config, List.of(), List.of(), Map.of(), Map.of(), foldMetricsByHorizon,
                List.of(), WalkForwardRuntimeReport.empty(), manifest);
    }
}
