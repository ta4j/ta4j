/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
