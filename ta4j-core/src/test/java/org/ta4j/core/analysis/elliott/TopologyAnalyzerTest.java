/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DoubleNum;

class TopologyAnalyzerTest {

    @Test
    void reportsInsufficientHistoryForASinglePivot() {
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5, pivots(10));

        assertThat(analysis.status()).isEqualTo(TopologyStatus.INSUFFICIENT_HISTORY);
        assertThat(analysis.candidates()).isEmpty();
    }

    @Test
    void matchesCompleteBullishMotive() {
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5,
                pivots(10, 20, 14, 26, 18, 32));

        assertThat(analysis.status()).isEqualTo(TopologyStatus.COMPLETE);
        assertThat(analysis.direction()).isEqualTo(WaveDirection.BULLISH);
        assertThat(analysis.candidates()).hasSize(1);
        assertThat(analysis.candidates().get(0).startBarIndex()).isEqualTo(0);
        assertThat(analysis.candidates().get(0).endBarIndex()).isEqualTo(5);
    }

    @Test
    void mirrorsCompleteBearishMotive() {
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5,
                pivots(32, 20, 26, 14, 24, 8));

        assertThat(analysis.status()).isEqualTo(TopologyStatus.COMPLETE);
        assertThat(analysis.direction()).isEqualTo(WaveDirection.BEARISH);
    }

    @Test
    void matchesCorrectiveThreeAgainstTheDeclaredTrend() {
        final TopologyAnalysis bullishTrendCorrection = new TopologyAnalyzer().analyze(TopologyGrammar.CORRECTIVE_3,
                pivots(20, 12, 16, 10));
        final TopologyAnalysis bearishTrendCorrection = new TopologyAnalyzer().analyze(TopologyGrammar.CORRECTIVE_3,
                pivots(10, 18, 14, 22));

        assertThat(bullishTrendCorrection.status()).isEqualTo(TopologyStatus.COMPLETE);
        assertThat(bullishTrendCorrection.direction()).isEqualTo(WaveDirection.BULLISH);
        assertThat(bearishTrendCorrection.status()).isEqualTo(TopologyStatus.COMPLETE);
        assertThat(bearishTrendCorrection.direction()).isEqualTo(WaveDirection.BEARISH);
    }

    @Test
    void refusesFormingClaimsFromASingleDecisiveLeg() {
        final TopologyAnalyzer analyzer = new TopologyAnalyzer();

        // A lone rising trailing leg fits some orientation of every kernel
        // grammar, so a two-pivot suffix must not be reported as FORMING;
        // otherwise nearly every genuine non-match folds into forming.
        assertThat(analyzer.analyze(TopologyGrammar.MOTIVE_5, pivots(10, 20, 15, 16, 17, 18)).status())
                .isEqualTo(TopologyStatus.NO_MATCH);
        // A corrective two-pivot window has only one uninformative leg;
        // its three-pivot state pins both legs and may form legitimately.
        assertThat(analyzer.analyze(TopologyGrammar.CORRECTIVE_3, pivots(10, 20)).status())
                .isEqualTo(TopologyStatus.NO_MATCH);
        assertThat(analyzer.analyze(TopologyGrammar.CORRECTIVE_3, pivots(10, 20, 15)).status())
                .isEqualTo(TopologyStatus.FORMING);
    }

    @Test
    void reportsFormingPrefixWhileTheStructureIsIncomplete() {
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5,
                pivots(10, 20, 14, 25));

        assertThat(analysis.status()).isEqualTo(TopologyStatus.FORMING);
        assertThat(analysis.direction()).isEqualTo(WaveDirection.BULLISH);
        assertThat(analysis.candidates()).isEmpty();
    }

    @Test
    void prefersLongestFormingSuffixBeforeChoosingDirection() {
        // The five-pivot suffix is a bearish motive prefix; its final four
        // pivots also form a bullish prefix. The stronger, longer suffix wins.
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5,
                pivots(30, 20, 25, 15, 22));

        assertThat(analysis.status()).isEqualTo(TopologyStatus.FORMING);
        assertThat(analysis.direction()).isEqualTo(WaveDirection.BEARISH);
    }

    @Test
    void keepsMateriallyTiedCandidatesAmbiguous() {
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5,
                pivots(10, 20, 14, 26, 18, 32, 24, 40, 30, 48, 36, 19));

        assertThat(analysis.status()).isEqualTo(TopologyStatus.AMBIGUOUS);
        assertThat(analysis.candidates()).hasSize(4);
        assertThat(analysis.candidates().stream().map(TopologyCandidate::startBarIndex).sorted().toList())
                .containsExactly(0, 2, 4, 5);
    }

    @Test
    void completesCycleFiveThreeWithMotiveAndCorrection() {
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.CYCLE_5_3,
                pivots(10, 20, 14, 26, 18, 32, 22, 27, 19));

        assertThat(analysis.status()).isEqualTo(TopologyStatus.COMPLETE);
        assertThat(analysis.direction()).isEqualTo(WaveDirection.BULLISH);
        assertThat(analysis.candidates().get(0).endBarIndex()).isEqualTo(8);
    }

    @Test
    void invalidatesWhenALaterPivotBreachesTheOrigin() {
        // Bullish motive 10 -> 32 completed on pivots 0..5; pivot 6 breaks the origin
        // low.
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5,
                pivots(10, 20, 14, 26, 18, 32, 9));

        assertThat(analysis.status()).isEqualTo(TopologyStatus.INVALIDATED);
    }

    @Test
    void isDeterministicAcrossRepeatedEvaluations() {
        final List<ConfirmedPivot> history = pivots(10, 20, 14, 26, 18, 32, 24, 40, 30, 48, 36, 19);
        final TopologyAnalysis first = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5, history);
        final TopologyAnalysis second = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5, history);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void respectsAsOfViewsSoUnconfirmedPivotsAreInvisible() {
        final PivotHistory history = PivotHistory
                .of(List.of(new ConfirmedPivot(0, 0, DoubleNum.valueOf(10), SwingPivotType.LOW),
                        new ConfirmedPivot(1, 1, DoubleNum.valueOf(20), SwingPivotType.HIGH),
                        new ConfirmedPivot(2, 7, DoubleNum.valueOf(14), SwingPivotType.LOW)));
        final TopologyAnalyzer analyzer = new TopologyAnalyzer();

        final TopologyAnalysis beforeAnySecondPivot = analyzer.analyze(TopologyGrammar.MOTIVE_5, history, 0);
        final TopologyAnalysis midFormation = analyzer.analyze(TopologyGrammar.MOTIVE_5, history, 6);
        final TopologyAnalysis afterConfirmation = analyzer.analyze(TopologyGrammar.MOTIVE_5, history, 7);

        assertThat(beforeAnySecondPivot.status()).isEqualTo(TopologyStatus.INSUFFICIENT_HISTORY);
        // Two visible pivots pin a single leg, which fits some orientation
        // of every grammar -- too weak to claim FORMING.
        assertThat(midFormation.status()).isEqualTo(TopologyStatus.NO_MATCH);
        assertThat(afterConfirmation.status()).isEqualTo(TopologyStatus.FORMING);
        assertThat(afterConfirmation.direction()).isEqualTo(WaveDirection.BULLISH);
    }

    @Test
    void rejectsDegenerateHistoryBounds() {
        assertThatThrownBy(() -> new TopologyAnalyzer(1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<ConfirmedPivot> pivots(final double... prices) {
        final List<ConfirmedPivot> result = new ArrayList<>(prices.length);
        for (int i = 0; i < prices.length; i++) {
            final SwingPivotType type = i % 2 == 0 ? SwingPivotType.LOW : SwingPivotType.HIGH;
            result.add(new ConfirmedPivot(i, i, DoubleNum.valueOf(prices[i]), type));
        }
        return result;
    }
}
