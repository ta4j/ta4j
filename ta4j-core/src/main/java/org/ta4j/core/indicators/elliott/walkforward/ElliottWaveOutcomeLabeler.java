/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.num.Num;
import org.ta4j.core.walkforward.OutcomeLabeler;
import org.ta4j.core.walkforward.RankedPrediction;

/**
 * Builds fixed-horizon realized outcomes for Elliott predictions.
 *
 * @since 0.22.4
 */
public final class ElliottWaveOutcomeLabeler
        implements OutcomeLabeler<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> {

    @Override
    public ElliottWaveOutcome label(BarSeries fullSeries, int decisionIndex, int horizonBars,
            RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment> prediction) {
        Objects.requireNonNull(fullSeries, "fullSeries");
        Objects.requireNonNull(prediction, "prediction");

        if (decisionIndex < fullSeries.getBeginIndex() || decisionIndex > fullSeries.getEndIndex()) {
            throw new IllegalArgumentException("decisionIndex out of range");
        }
        if (horizonBars <= 0) {
            throw new IllegalArgumentException("horizonBars must be > 0");
        }

        ElliottScenario scenario = prediction.payload().scenario();
        if (scenario == null || !scenario.hasKnownDirection()) {
            return new ElliottWaveOutcome(ElliottWaveOutcome.EventOutcome.NEITHER,
                    ElliottWaveOutcome.PhaseProgression.UNKNOWN, Double.NaN, false, false);
        }

        int labelStart = decisionIndex + 1;
        int labelEnd = Math.min(fullSeries.getEndIndex(), decisionIndex + horizonBars);
        if (labelStart > labelEnd) {
            return new ElliottWaveOutcome(ElliottWaveOutcome.EventOutcome.NEITHER,
                    ElliottWaveOutcome.PhaseProgression.UNKNOWN, Double.NaN, false, false);
        }

        Num startClose = fullSeries.getBar(decisionIndex).getClosePrice();
        Num endClose = fullSeries.getBar(labelEnd).getClosePrice();

        boolean bullish = scenario.isBullish();
        Num target = scenario.primaryTarget();
        Num invalidation = scenario.invalidationPrice();

        ElliottWaveOutcome.EventOutcome eventOutcome = ElliottWaveOutcome.EventOutcome.NEITHER;
        boolean reachedTarget = false;
        boolean breachedInvalidation = false;

        for (int i = labelStart; i <= labelEnd; i++) {
            Num close = fullSeries.getBar(i).getClosePrice();
            Num high = Num.isValid(fullSeries.getBar(i).getHighPrice()) ? fullSeries.getBar(i).getHighPrice() : close;
            Num low = Num.isValid(fullSeries.getBar(i).getLowPrice()) ? fullSeries.getBar(i).getLowPrice() : close;

            boolean targetTouched = targetTouch(bullish, target, high, low);
            boolean invalidationTouched = invalidationTouch(bullish, invalidation, high, low);

            if (targetTouched && invalidationTouched) {
                reachedTarget = true;
                breachedInvalidation = true;
                eventOutcome = ElliottWaveOutcome.EventOutcome.INVALIDATION_FIRST;
                break;
            }
            if (targetTouched) {
                reachedTarget = true;
                eventOutcome = ElliottWaveOutcome.EventOutcome.TARGET_FIRST;
                break;
            }
            if (invalidationTouched) {
                breachedInvalidation = true;
                eventOutcome = ElliottWaveOutcome.EventOutcome.INVALIDATION_FIRST;
                break;
            }
        }

        double realizedReturn = Double.NaN;
        if (Num.isValid(startClose) && Num.isValid(endClose) && !startClose.isZero()) {
            realizedReturn = endClose.minus(startClose).dividedBy(startClose).doubleValue();
        }

        ElliottWaveOutcome.PhaseProgression phaseProgression = progression(bullish, startClose, endClose);
        return new ElliottWaveOutcome(eventOutcome, phaseProgression, realizedReturn, reachedTarget,
                breachedInvalidation);
    }

    private static ElliottWaveOutcome.PhaseProgression progression(boolean bullish, Num startClose, Num endClose) {
        if (Num.isNaNOrNull(startClose) || Num.isNaNOrNull(endClose)) {
            return ElliottWaveOutcome.PhaseProgression.UNKNOWN;
        }
        int direction = endClose.compareTo(startClose);
        if (direction == 0) {
            return ElliottWaveOutcome.PhaseProgression.STALLED;
        }
        if ((bullish && direction > 0) || (!bullish && direction < 0)) {
            return ElliottWaveOutcome.PhaseProgression.ADVANCING;
        }
        return ElliottWaveOutcome.PhaseProgression.REVERSING;
    }

    private static boolean targetTouch(boolean bullish, Num target, Num high, Num low) {
        if (Num.isNaNOrNull(target) || Num.isNaNOrNull(high) || Num.isNaNOrNull(low)) {
            return false;
        }
        return bullish ? high.isGreaterThanOrEqual(target) : low.isLessThanOrEqual(target);
    }

    private static boolean invalidationTouch(boolean bullish, Num invalidation, Num high, Num low) {
        if (Num.isNaNOrNull(invalidation) || Num.isNaNOrNull(high) || Num.isNaNOrNull(low)) {
            return false;
        }
        return bullish ? low.isLessThanOrEqual(invalidation) : high.isGreaterThanOrEqual(invalidation);
    }
}
