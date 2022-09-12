/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package ta4jexamples.logging;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.Strategy;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.CCICorrectionStrategy;

/**
 * Strategy execution logging example.
 * * 策略执行记录示例。
 */
public class StrategyExecutionLogging {

    private static final URL LOGBACK_CONF_FILE = StrategyExecutionLogging.class.getClassLoader()
            .getResource("logback-traces.xml");

    /**
     * Loads the Logback configuration from a resource file. Only here to avoid polluting other examples with logs. Could be replaced by a simple logback.xml file in the resource folder.
     * * 从资源文件加载 Logback 配置。 仅在此处避免使用日志污染其他示例。 可以用资源文件夹中的简单 logback.xml 文件替换。
     */
    private static void loadLoggerConfiguration() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        try {
            configurator.doConfigure(LOGBACK_CONF_FILE);
        } catch (JoranException je) {
            Logger.getLogger(StrategyExecutionLogging.class.getName()).log(Level.SEVERE,
                    "Unable to load Logback configuration 无法加载 Logback 配置", je);
        }
    }

    private static void unloadLoggerConfiguration() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
    }

    public static void main(String[] args) {
        // Loading the Logback configuration
        // 加载 Logback 配置
        loadLoggerConfiguration();

        // Getting the bar series
        // 获取柱状系列
        BarSeries series = CsvTradesLoader.loadBitstampSeries();

        // Building the trading strategy
        // 构建交易策略
        Strategy strategy = CCICorrectionStrategy.buildStrategy(series);

        // Running the strategy
        // 运行策略
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        seriesManager.run(strategy);

        // Unload the Logback configuration
        // 卸载 Logback 配置
        unloadLoggerConfiguration();
    }
}
