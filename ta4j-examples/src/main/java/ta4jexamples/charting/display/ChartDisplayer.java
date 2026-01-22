/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.display;

import org.jfree.chart.JFreeChart;

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
}
