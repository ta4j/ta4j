package ta4jexamples.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TimeSeriesManager;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.CCICorrectionStrategy;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Strategy execution logging example.
 * </p>
 */
public class StrategyExecutionLogging {

    private static final URL LOGBACK_CONF_FILE = StrategyExecutionLogging.class.getClassLoader().getResource("logback-traces.xml");
    
    /**
     * Loads the Logback configuration from a resource file.
     * Only here to avoid polluting other examples with logs. Could be replaced by a simple logback.xml file in the resource folder.
     */
    private static void loadLoggerConfiguration() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        try {
            configurator.doConfigure(LOGBACK_CONF_FILE);
        } catch (JoranException je) {
            Logger.getLogger(StrategyExecutionLogging.class.getName()).log(Level.SEVERE, "Unable to load Logback configuration", je);
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
        loadLoggerConfiguration();

        // Getting the time series
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();

        // Building the trading strategy
        Strategy strategy = CCICorrectionStrategy.buildStrategy(series);

        // Running the strategy
        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        seriesManager.run(strategy);

        // Unload the Logback configuration
        unloadLoggerConfiguration();
    }
}
