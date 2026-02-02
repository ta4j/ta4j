/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.logging;

import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.Configurator;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BarSeriesManager;

import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;
import ta4jexamples.strategies.CCICorrectionStrategy;

/**
 * Strategy execution logging example.
 */
public class StrategyExecutionLogging {

    private static final Logger LOGGER = LogManager.getLogger(StrategyExecutionLogging.class);
    private static final URL LOG4J_CONFIGURATION = StrategyExecutionLogging.class.getClassLoader()
            .getResource("log4j2-traces.xml");
    private static String previousConfigurationFile;

    /**
     * Loads the Log4j configuration from a resource file. Only here to avoid
     * polluting other examples with logs. Could be replaced by a simple log4j2.xml
     * file in the resource folder.
     */
    private static void loadLoggerConfiguration() {
        if (LOG4J_CONFIGURATION == null) {
            LOGGER.warn("Unable to locate log4j2-traces.xml on the classpath");
            return;
        }

        if (previousConfigurationFile == null) {
            previousConfigurationFile = System.getProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY);
        }

        System.setProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY, LOG4J_CONFIGURATION.toString());
        try {
            Configurator.reconfigure();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to load Log4j configuration", exception);
            restorePreviousConfiguration();
        }
    }

    private static void unloadLoggerConfiguration() {
        restorePreviousConfiguration();
    }

    private static void restorePreviousConfiguration() {
        if (previousConfigurationFile == null) {
            System.clearProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY);
        } else {
            System.setProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY, previousConfigurationFile);
        }

        Configurator.reconfigure();
        previousConfigurationFile = null;
    }

    public static void main(String[] args) {
        // Loading the Log4j configuration
        loadLoggerConfiguration();

        // Getting the bar series
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();

        // Building the trading strategy
        Strategy strategy = CCICorrectionStrategy.buildStrategy(series);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        seriesManager.run(strategy);

        // Unload the Log4j configuration
        unloadLoggerConfiguration();
    }
}
