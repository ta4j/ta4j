/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.display;

import org.jfree.chart.JFreeChart;

import java.awt.Dimension;

/**
 * Display strategy for {@link JFreeChart} instances.
 *
 * <p>
 * Implementations are responsible for presenting a chart to the user. The
 * default implementation is {@link SwingChartDisplayer}, which renders a chart
 * in a Swing {@code ApplicationFrame}.
 * </p>
 *
 * @since 0.19
 */
public interface ChartDisplayer {

    /**
     * Presents the provided chart to the user.
     *
     * @param chart the chart to display
     * @since 0.19
     */
    void display(JFreeChart chart);

    /**
     * Presents the provided chart to the user with a custom window title.
     *
     * @param chart       the chart to display
     * @param windowTitle the title for the window/frame
     * @since 0.19
     */
    void display(JFreeChart chart, String windowTitle);

    /**
     * Presents the provided chart to the user with a custom window title and an
     * explicit preferred display size.
     *
     * <p>
     * Implementations may ignore the preferred size when they cannot honor it. The
     * default behavior delegates to the existing title-aware overload so current
     * implementations remain source-compatible.
     * </p>
     *
     * @param chart         the chart to display
     * @param windowTitle   the title for the window/frame
     * @param preferredSize the preferred display size
     * @since 0.22.4
     */
    default void display(JFreeChart chart, String windowTitle, Dimension preferredSize) {
        if (windowTitle != null && !windowTitle.trim().isEmpty()) {
            display(chart, windowTitle);
        } else {
            display(chart);
        }
    }
}
