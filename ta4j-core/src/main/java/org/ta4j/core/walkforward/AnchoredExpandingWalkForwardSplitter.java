/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;

/**
 * Default splitter using anchored expanding training windows and rolling test
 * windows.
 *
 * <p>
 * Every fold keeps {@code trainStart} anchored at the first series index while
 * increasing {@code trainEnd} over time. Test windows advance by
 * {@code stepBars}, and split boundaries apply both purge and embargo offsets.
 *
 * @since 0.22.4
 */
public final class AnchoredExpandingWalkForwardSplitter implements WalkForwardSplitter {

    @Override
    public List<WalkForwardSplit> split(BarSeries series, WalkForwardConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(config, "config");
        if (series.isEmpty()) {
            return List.of();
        }

        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        int evaluationEnd = end - config.holdoutBars();
        if (evaluationEnd <= begin) {
            return List.of();
        }

        List<WalkForwardSplit> folds = new ArrayList<>();

        int testStart = begin + config.minTrainBars() + config.purgeBars() + config.embargoBars();
        int foldNumber = 1;

        while (testStart <= evaluationEnd) {
            int trainEnd = testStart - config.purgeBars() - config.embargoBars() - 1;
            if (trainEnd - begin + 1 < config.minTrainBars()) {
                break;
            }

            int testEnd = Math.min(testStart + config.testBars() - 1, evaluationEnd);
            folds.add(new WalkForwardSplit("fold-" + foldNumber, begin, trainEnd, testStart, testEnd,
                    config.purgeBars(), config.embargoBars(), false));

            foldNumber++;
            testStart += config.stepBars();
        }

        if (config.holdoutBars() > 0) {
            int holdoutStart = evaluationEnd + 1;
            int holdoutEnd = end;
            if (holdoutStart <= holdoutEnd) {
                int holdoutTrainEnd = holdoutStart - config.purgeBars() - config.embargoBars() - 1;
                if (holdoutTrainEnd - begin + 1 >= config.minTrainBars()) {
                    folds.add(new WalkForwardSplit("holdout", begin, holdoutTrainEnd, holdoutStart, holdoutEnd,
                            config.purgeBars(), config.embargoBars(), true));
                }
            }
        }

        return List.copyOf(folds);
    }
}
