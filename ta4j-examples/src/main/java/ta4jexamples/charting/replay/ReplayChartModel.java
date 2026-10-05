/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.replay.ReplayArtifact.PriceBar;
import ta4jexamples.charting.replay.ReplayFrame.Candidate;
import ta4jexamples.charting.replay.ReplayFrame.Pivot;
import ta4jexamples.charting.replay.ReplayFrame.Point;

/**
 * Chart-ready projection of a {@link ReplayFrame}: a bar series that ends at
 * the cursor plus one annotation layer per drawn element.
 *
 * <p>
 * The series holds only bars {@code windowStart..cursor}, so no layer can
 * reference a later bar. Layer label indices are window-relative; add
 * {@link #windowStart()} to get the source bar index.
 *
 * @param title       chart title
 * @param series      the visible bars, window index 0 is source bar
 *                    {@code windowStart}
 * @param windowStart source index of window bar 0
 * @param layers      overlay layers in draw order
 * @param axisLow     lower bound of the visible price axis
 * @param axisHigh    upper bound of the visible price axis
 */
record ReplayChartModel(String title, BarSeries series, int windowStart, List<Layer> layers, double axisLow,
        double axisHigh) {

    private static final Color[] PALETTE = { new Color(0xFF9800), new Color(0x4CAF50), new Color(0xE91E63),
            new Color(0x03A9F4), new Color(0x9C27B0), new Color(0xCDDC39), new Color(0x795548), new Color(0x00BCD4) };
    private static final Color PIVOT_COLOR = new Color(0xB0BEC5);
    private static final Color CONFIRMATION_COLOR = new Color(0xFFEB3B);

    /**
     * One overlay.
     *
     * @param name      legend text
     * @param color     line and label color
     * @param width     line width
     * @param line      indicator drawn as a connected polyline through the labeled
     *                  bars, or {@code null} for marker-only layers
     * @param indicator indicator carrying values and labels
     * @param candidate candidate ordinal ({@code -1} for non-candidate layers)
     */
    record Layer(String name, Color color, float width, Indicator<Num> line, BarSeriesLabelIndicator indicator,
            int candidate) {

        /**
         * @return the labels with window-relative indices
         */
        List<BarLabel> labels() {
            return indicator.labels();
        }
    }

    /**
     * Values exist only on the labeled bars; connecting the non-NaN values draws
     * the polyline through the recorded points and nothing else.
     */
    private static final class PointLine extends CachedIndicator<Num> {

        private final java.util.Map<Integer, Num> values = new java.util.HashMap<>();

        PointLine(final BarSeries series, final List<BarLabel> labels) {
            super(series);
            labels.forEach(label -> values.put(label.barIndex(), label.yValue()));
        }

        @Override
        protected Num calculate(final int index) {
            final Num value = values.get(index);
            return value == null ? NaN.NaN : value;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    ReplayChartModel {
        layers = List.copyOf(layers);
    }

    /**
     * Builds the model from a frame and the dataset bars.
     *
     * @param frame the frame
     * @param bars  all retained bars; only {@code windowStart..cursor} is read
     * @return the model
     */
    static ReplayChartModel of(final ReplayFrame frame, final List<PriceBar> bars) {
        final BarSeries series = ReplayArtifact.series(bars, frame.comparisonKey(), frame.windowStart(),
                frame.cursor());
        final int offset = frame.windowStart();
        final List<Layer> layers = new ArrayList<>();
        final List<BarLabel> pivotLabels = new ArrayList<>();
        final List<BarLabel> confirmationLabels = new ArrayList<>();
        for (final Pivot pivot : frame.pivots()) {
            if (pivot.inWindow()) {
                pivotLabels.add(new BarLabel(pivot.index() - offset, num(series, pivot.price()),
                        ("HIGH".equals(pivot.type()) ? "H" : "L") + pivot.index(),
                        "HIGH".equals(pivot.type()) ? LabelPlacement.ABOVE : LabelPlacement.BELOW, PIVOT_COLOR));
            }
            if (pivot.confirmationIndex() >= offset) {
                final PriceBar confirmationBar = bars.get(pivot.confirmationIndex());
                confirmationLabels.add(new BarLabel(pivot.confirmationIndex() - offset,
                        num(series, confirmationBar.close()), "c" + pivot.index(), LabelPlacement.CENTER,
                        pivot.newlyConfirmed() ? Color.WHITE : CONFIRMATION_COLOR));
            }
        }
        layers.add(new Layer("Confirmed pivots", PIVOT_COLOR, 1.0f, new PointLine(series, pivotLabels),
                new BarSeriesLabelIndicator(series, pivotLabels), -1));
        layers.add(new Layer("Confirmation bars (cN = pivot N confirmed)", CONFIRMATION_COLOR, 0.5f, null,
                new BarSeriesLabelIndicator(series, confirmationLabels), -1));
        for (final Candidate candidate : frame.candidates()) {
            if (!candidate.overlayed()) {
                continue;
            }
            final List<BarLabel> labels = new ArrayList<>();
            final Color color = PALETTE[candidate.ordinal() % PALETTE.length];
            for (final Point point : candidate.placement()) {
                if (point.inWindow()) {
                    labels.add(new BarLabel(point.index() - offset, num(series, point.price()),
                            "#" + (candidate.ordinal() + 1) + (candidate.selected() ? "*" : ""),
                            "HIGH".equals(point.type()) ? LabelPlacement.ABOVE : LabelPlacement.BELOW, color));
                }
            }
            layers.add(new Layer(
                    "#" + (candidate.ordinal() + 1) + " " + shortKey(candidate.candidateKey()) + " "
                            + candidate.direction(),
                    color, candidate.selected() ? 3.0f : 1.6f, new PointLine(series, labels),
                    new BarSeriesLabelIndicator(series, labels), candidate.ordinal()));
        }
        final BarLabel asOf = new BarLabel(frame.cursor() - offset, series.getLastBar().getHighPrice(),
                "as-of " + frame.cursor(), LabelPlacement.ABOVE, Color.WHITE);
        layers.add(new Layer("As-of bar", Color.WHITE, 0.5f, null, new BarSeriesLabelIndicator(series, List.of(asOf)),
                -1));
        final double low = new BigDecimal(frame.axis().low()).doubleValue();
        final double high = new BigDecimal(frame.axis().high()).doubleValue();
        final double margin = Math.max((high - low) * 0.04, Math.ulp(high) * 8);
        return new ReplayChartModel(title(frame), series, offset, layers, low - margin, high + margin);
    }

    private static String title(final ReplayFrame frame) {
        return frame.comparisonKey() + " | bar " + frame.cursor() + " " + frame.asOfTime() + " | " + frame.status()
                + (frame.direction().isEmpty() ? "" : " " + frame.direction()) + " | " + frame.candidates().size()
                + " candidate(s)" + (frame.truncated() > 0 ? ", " + frame.truncated() + " not drawn" : "");
    }

    private static String shortKey(final String key) {
        return key.length() > 8 ? key.substring(0, 8) : key;
    }

    private static Num num(final BarSeries series, final String value) {
        return series.numFactory().numOf(value);
    }
}
