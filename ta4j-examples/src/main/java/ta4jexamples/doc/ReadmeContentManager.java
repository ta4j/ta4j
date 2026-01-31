/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.doc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.*;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.RuleSerialization;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to manage README content including chart images, code snippets,
 * and documentation.
 *
 * <p>
 * This class serves as the single source of truth for README content. It
 * generates chart images, extracts code snippets from source code using special
 * comment markers, and synchronizes the README with the latest code examples.
 * </p>
 *
 * <p>
 * <strong>How it works:</strong>
 * <ul>
 * <li>Code snippets are marked with {@code // START_SNIPPET: snippet-id} and
 * {@code // END_SNIPPET: snippet-id} comments in the chart generation
 * methods</li>
 * <li>The README.md file contains corresponding HTML comment markers:
 * {@code <!-- START_SNIPPET: snippet-id -->} and
 * {@code <!-- END_SNIPPET: snippet-id -->}</li>
 * <li>Run with argument "update-readme" to automatically extract snippets and
 * update the README:
 * {@code mvn -pl ta4j-examples exec:java -Dexec.mainClass=ta4jexamples.doc.ReadmeContentManager -Dexec.args=update-readme}</li>
 * </ul>
 * </p>
 *
 * <p>
 * <strong>Usage:</strong>
 * <ul>
 * <li>Generate charts and update README (default):
 * {@code mvn -pl ta4j-examples exec:java -Dexec.mainClass=ta4jexamples.doc.ReadmeContentManager}</li>
 * <li>Update README snippets only:
 * {@code mvn -pl ta4j-examples exec:java -Dexec.mainClass=ta4jexamples.doc.ReadmeContentManager -Dexec.args=update-readme}</li>
 * <li>View snippets only:
 * {@code mvn -pl ta4j-examples exec:java -Dexec.mainClass=ta4jexamples.doc.ReadmeContentManager -Dexec.args=snippets}</li>
 * </ul>
 * </p>
 */
public class ReadmeContentManager {

    private static final Logger LOG = LogManager.getLogger(ReadmeContentManager.class);

    /**
     * Generates the EMA Crossover chart matching the README quick start example.
     *
     * @param outputDir the directory to save the chart image (e.g., "docs/charts"
     *                  or ".")
     * @return the path to the saved chart image, or empty if saving failed
     */
    public static Optional<Path> generateEmaCrossoverChart(String outputDir) {
        LOG.info("Generating EMA Crossover chart for README...");

        // Load historical price data
        BarSeries fullSeries = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        LOG.info("Loaded {} bars from Bitstamp series", fullSeries.getBarCount());

        // Use a smaller subset for a cleaner chart (last ~150 bars)
        int totalBars = fullSeries.getBarCount();
        int startIndex = Math.max(0, totalBars - 150);
        BarSeries series = fullSeries.getSubSeries(startIndex, totalBars);
        LOG.info("Using subset: {} bars (indices {} to {})", series.getBarCount(), startIndex, totalBars - 1);

        // Create indicators: calculate moving averages from close prices
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator fastEma = new EMAIndicator(close, 12); // 12-period EMA
        EMAIndicator slowEma = new EMAIndicator(close, 26); // 26-period EMA

        // Define entry rule: buy when fast EMA crosses above slow EMA (golden cross)
        Rule entry = new CrossedUpIndicatorRule(fastEma, slowEma);

        // Define exit rule: sell when price gains 3% OR loses 1.5%
        Rule exit = new StopGainRule(close, 3.0) // take profit at +3%
                .or(new StopLossRule(close, 1.5)); // or cut losses at -1.5%

        // Combine rules into a strategy
        Strategy strategy = new BaseStrategy("EMA Crossover", entry, exit);

        // Run the strategy on historical data
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        LOG.info("Strategy executed: {} positions", record.getPositionCount());

        // START_SNIPPET: ema-crossover
        // Generate simplified chart - just price, indicators, and signals (no subchart)
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withTitle("EMA Crossover Strategy")
                .withSeries(series) // Price bars (candlesticks)
                .withIndicatorOverlay(fastEma) // Overlay indicators on price chart
                .withIndicatorOverlay(slowEma)
                .withTradingRecordOverlay(record) // Mark entry/exit points with arrows
                .toChart();
        chartWorkflow.saveChartImage(chart, series, "ema-crossover-strategy", "output/charts"); // Save as image
        // END_SNIPPET: ema-crossover

        // Display chart in interactive window (only in non-headless environments)
        if (!GraphicsEnvironment.isHeadless()) {
            chartWorkflow.displayChart(chart);
        }

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(chart, series, "ema-crossover-readme", outputDir);

        if (savedPath.isPresent()) {
            Path relativePath = getRelativePath(savedPath.get());
            LOG.info("Chart saved to: {}", relativePath);
        } else {
            LOG.warn("Failed to save ema-crossover-readme.jpg chart image");
        }

        return savedPath;
    }

    /**
     * Generates an RSI Strategy chart with RSI indicator in a subchart.
     * Demonstrates using subcharts for indicators with different scales.
     *
     * @param outputDir the directory to save the chart image
     * @return the path to the saved chart image, or empty if saving failed
     */
    public static Optional<Path> generateRsiStrategyChart(String outputDir) {
        LOG.info("Generating RSI Strategy chart with subchart for README...");

        // Load historical price data
        BarSeries fullSeries = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        LOG.info("Loaded {} bars from Bitstamp series", fullSeries.getBarCount());

        // Use a smaller subset for a cleaner chart (last ~150 bars)
        int totalBars = fullSeries.getBarCount();
        int startIndex = Math.max(0, totalBars - 150);
        BarSeries series = fullSeries.getSubSeries(startIndex, totalBars);
        LOG.info("Using subset: {} bars (indices {} to {})", series.getBarCount(), startIndex, totalBars - 1);

        // START_SNIPPET: rsi-strategy
        // Create indicators
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, 14);

        // RSI strategy: buy when RSI crosses below 30 (oversold), sell when RSI crosses
        // above 70 (overbought)
        Rule entry = new CrossedDownIndicatorRule(rsi, 30);
        Rule exit = new CrossedUpIndicatorRule(rsi, 70);
        Strategy strategy = new BaseStrategy("RSI Strategy", entry, exit);
        TradingRecord record = new BarSeriesManager(series).run(strategy);

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withTitle("RSI Strategy with Subchart")
                .withSeries(series) // Price bars (candlesticks)
                .withTradingRecordOverlay(record) // Mark entry/exit points
                .withSubChart(rsi) // RSI indicator in separate subchart panel
                .toChart();
        // END_SNIPPET: rsi-strategy

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(chart, series, "rsi-strategy-readme", outputDir);

        if (savedPath.isPresent()) {
            Path relativePath = getRelativePath(savedPath.get());
            LOG.info("Chart saved to: {}", relativePath);
        } else {
            LOG.warn("Failed to save rsi-strategy-readme.jpg chart image");
        }

        return savedPath;
    }

    /**
     * Generates a strategy chart with performance metrics subchart. Demonstrates
     * visualizing performance analysis criteria over time.
     *
     * @param outputDir the directory to save the chart image
     * @return the path to the saved chart image, or empty if saving failed
     */
    public static Optional<Path> generateStrategyPerformanceChart(String outputDir) {
        LOG.info("Generating Strategy Performance chart with metrics subchart for README...");

        // Load historical price data
        BarSeries fullSeries = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        LOG.info("Loaded {} bars from Bitstamp series", fullSeries.getBarCount());

        // Use a smaller subset for a cleaner chart (last ~150 bars)
        int totalBars = fullSeries.getBarCount();
        int startIndex = Math.max(0, totalBars - 150);
        BarSeries series = fullSeries.getSubSeries(startIndex, totalBars);
        LOG.info("Using subset: {} bars (indices {} to {})", series.getBarCount(), startIndex, totalBars - 1);

        // START_SNIPPET: strategy-performance
        // Create indicators: multiple moving averages
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma20 = new SMAIndicator(close, 20);
        EMAIndicator ema12 = new EMAIndicator(close, 12);

        // Strategy: buy when EMA crosses above SMA, sell when EMA crosses below SMA
        Rule entry = new CrossedUpIndicatorRule(ema12, sma20);
        Rule exit = new CrossedDownIndicatorRule(ema12, sma20);
        Strategy strategy = new BaseStrategy("EMA/SMA Crossover", entry, exit);
        TradingRecord record = new BarSeriesManager(series).run(strategy);

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withTitle("Strategy Performance Analysis")
                .withSeries(series) // Price bars (candlesticks)
                .withIndicatorOverlay(sma20) // Overlay SMA on price chart
                .withIndicatorOverlay(ema12) // Overlay EMA on price chart
                .withTradingRecordOverlay(record) // Mark entry/exit points
                .withSubChart(new MaximumDrawdownCriterion(), record) // Performance metric in subchart
                .toChart();
        // END_SNIPPET: strategy-performance

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(chart, series, "strategy-performance-readme",
                outputDir);

        if (savedPath.isPresent()) {
            Path relativePath = getRelativePath(savedPath.get());
            LOG.info("Chart saved to: {}", relativePath);
        } else {
            LOG.warn("Failed to save strategy-performance-readme.jpg chart image");
        }

        return savedPath;
    }

    /**
     * Generates an advanced multi-indicator strategy chart with multiple subcharts.
     * Demonstrates full charting capabilities with multiple analysis layers.
     *
     * @param outputDir the directory to save the chart image
     * @return the path to the saved chart image, or empty if saving failed
     */
    public static Optional<Path> generateAdvancedStrategyChart(String outputDir) {
        LOG.info("Generating Advanced Multi-Indicator Strategy chart for README...");

        // Load historical price data
        BarSeries fullSeries = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        LOG.info("Loaded {} bars from Bitstamp series", fullSeries.getBarCount());

        // Use a smaller subset for a cleaner chart (last ~150 bars)
        int totalBars = fullSeries.getBarCount();
        int startIndex = Math.max(0, totalBars - 150);
        BarSeries series = fullSeries.getSubSeries(startIndex, totalBars);
        LOG.info("Using subset: {} bars (indices {} to {})", series.getBarCount(), startIndex, totalBars - 1);

        // START_SNIPPET: advanced-strategy
        // Create indicators
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma50 = new SMAIndicator(close, 50);
        EMAIndicator ema12 = new EMAIndicator(close, 12);
        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        RSIIndicator rsi = new RSIIndicator(close, 14);

        // Strategy: buy when EMA crosses above SMA and RSI > 50, sell when EMA crosses
        // below SMA
        Rule entry = new CrossedUpIndicatorRule(ema12, sma50).and(new OverIndicatorRule(rsi, 50));
        Rule exit = new CrossedDownIndicatorRule(ema12, sma50);
        Strategy strategy = new BaseStrategy("Advanced Multi-Indicator Strategy", entry, exit);
        TradingRecord record = new BarSeriesManager(series).run(strategy);

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withTitle("Advanced Multi-Indicator Strategy")
                .withSeries(series) // Price bars (candlesticks)
                .withIndicatorOverlay(sma50) // Overlay SMA on price chart
                .withIndicatorOverlay(ema12) // Overlay EMA on price chart
                .withTradingRecordOverlay(record) // Mark entry/exit points
                .withSubChart(macd) // MACD indicator in subchart
                .withSubChart(rsi) // RSI indicator in subchart
                .withSubChart(new NetProfitLossCriterion(), record) // Net profit/loss performance metric
                .toChart();
        // END_SNIPPET: advanced-strategy

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(chart, series, "advanced-strategy-readme", outputDir);

        if (savedPath.isPresent()) {
            Path relativePath = getRelativePath(savedPath.get());
            LOG.info("Chart saved to: {}", relativePath);
        } else {
            LOG.warn("Failed to save advanced-strategy-readme.jpg chart image");
        }

        return savedPath;
    }

    /**
     * Finds the project root directory by searching for README.md file. Starts from
     * the current working directory and walks up the directory tree.
     *
     * @return the project root path, or current directory if not found
     */
    private static Path findProjectRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        Path root = current.getRoot();

        // Walk up the directory tree looking for README.md
        while (current != null && !current.equals(root)) {
            Path readmePath = current.resolve("README.md");
            if (Files.exists(readmePath)) {
                return current;
            }
            current = current.getParent();
        }

        // If not found, return current working directory as fallback
        return Paths.get("").toAbsolutePath().normalize();
    }

    /**
     * Converts an absolute path to a relative path from the project root.
     *
     * @param absolutePath the absolute path to convert
     * @return the relative path, or the original path if conversion fails
     */
    private static Path getRelativePath(Path absolutePath) {
        try {
            Path projectRoot = findProjectRoot();
            Path normalized = absolutePath.normalize();
            if (normalized.startsWith(projectRoot)) {
                return projectRoot.relativize(normalized);
            }
            return normalized;
        } catch (Exception e) {
            LOG.warn("Failed to convert path to relative: {}", e.getMessage());
            return absolutePath;
        }
    }

    /**
     * Generates serialization examples for the README. This method demonstrates
     * serializing indicators, rules, and strategies to JSON. The examples are
     * extracted via snippet markers for inclusion in the README.
     *
     * @param series the bar series to use for examples
     */
    @SuppressWarnings("unused")
    public static void generateSerializationExamples(BarSeries series) {
        // START_SNIPPET: serialize-indicator
        // Serialize an indicator (RSI) to JSON
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, 14);
        String rsiJson = rsi.toJson();
        LOG.info("Output: {}", rsiJson);
        // Output:
        // {"type":"RSIIndicator","parameters":{"barCount":14},"components":[{"type":"ClosePriceIndicator"}]}
        // END_SNIPPET: serialize-indicator

        // START_SNIPPET: serialize-rule
        // Serialize a rule (AndRule) to JSON
        Rule rule1 = new OverIndicatorRule(rsi, 50);
        Rule rule2 = new UnderIndicatorRule(rsi, 80);
        Rule andRule = new AndRule(rule1, rule2);
        String ruleJson = ComponentSerialization.toJson(RuleSerialization.describe(andRule));
        LOG.info("Output: {}", ruleJson);
        // Output:
        // {"type":"AndRule","label":"AndRule","components":[{"type":"OverIndicatorRule","label":"OverIndicatorRule","components":[{"type":"RSIIndicator","parameters":{"barCount":14},"components":[{"type":"ClosePriceIndicator"}]}],"parameters":{"threshold":50.0}},{"type":"UnderIndicatorRule","label":"UnderIndicatorRule","components":[{"type":"RSIIndicator","parameters":{"barCount":14},"components":[{"type":"ClosePriceIndicator"}]}],"parameters":{"threshold":80.0}}]}
        // END_SNIPPET: serialize-rule

        // START_SNIPPET: serialize-strategy
        // Serialize a strategy (EMA Crossover) to JSON
        EMAIndicator fastEma = new EMAIndicator(close, 12);
        EMAIndicator slowEma = new EMAIndicator(close, 26);
        Rule entry = new CrossedUpIndicatorRule(fastEma, slowEma);
        Rule exit = new CrossedDownIndicatorRule(fastEma, slowEma);
        Strategy strategy = new BaseStrategy("EMA Crossover", entry, exit);
        String strategyJson = strategy.toJson();
        LOG.info("Output: {}", strategyJson);
        // Output: {"type":"BaseStrategy","label":"EMA
        // Crossover","parameters":{"unstableBars":0},"rules":[{"type":"CrossedUpIndicatorRule","label":"entry","components":[{"type":"EMAIndicator","parameters":{"barCount":12},"components":[{"type":"ClosePriceIndicator"}]},{"type":"EMAIndicator","parameters":{"barCount":26},"components":[{"type":"ClosePriceIndicator"}]}]},{"type":"CrossedDownIndicatorRule","label":"exit","components":[{"type":"EMAIndicator","parameters":{"barCount":12},"components":[{"type":"ClosePriceIndicator"}]},{"type":"EMAIndicator","parameters":{"barCount":26},"components":[{"type":"ClosePriceIndicator"}]}]}]}
        // END_SNIPPET: serialize-strategy
    }

    /**
     * Extracts code between START_SNIPPET and END_SNIPPET markers from the source
     * file.
     *
     * @param sourceFile the Java source file to read
     * @param snippetId  the snippet identifier (e.g., "ema-crossover")
     * @return the extracted code snippet, or empty if not found
     */
    public static Optional<String> extractCodeSnippet(Path sourceFile, String snippetId) {
        try {
            String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
            String startMarker = "// START_SNIPPET: " + snippetId;
            String endMarker = "// END_SNIPPET: " + snippetId;

            int startIndex = content.indexOf(startMarker);
            if (startIndex == -1) {
                LOG.warn("Start marker not found for snippet: {}", snippetId);
                return Optional.empty();
            }

            int endIndex = content.indexOf(endMarker, startIndex);
            if (endIndex == -1) {
                LOG.warn("End marker not found for snippet: {}", snippetId);
                return Optional.empty();
            }

            // Extract code between markers (excluding the markers themselves)
            int newlineIndex = content.indexOf('\n', startIndex);
            if (newlineIndex == -1) {
                LOG.warn("No newline found after start marker for snippet: {}", snippetId);
                return Optional.empty();
            }
            int codeStart = newlineIndex + 1;
            // Don't trim here - we need to preserve the exact indentation structure
            // We'll trim only trailing whitespace from the entire snippet after processing
            String snippet = content.substring(codeStart, endIndex);

            // Remove leading indentation (find minimum indentation and remove it)
            // Strategy: Find the minimum indentation among lines that have indentation > 0,
            // but if all lines have 0 indentation, use 0. This handles cases where the
            // first
            // line (e.g., a comment) has no indentation but subsequent lines do.
            String[] lines = snippet.split("\n");
            int minIndent = Integer.MAX_VALUE;
            int minIndentIncludingZero = Integer.MAX_VALUE;
            boolean hasIndentedLines = false;

            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    int indent = 0;
                    for (char c : line.toCharArray()) {
                        if (c == ' ') {
                            indent++;
                        } else {
                            break;
                        }
                    }
                    minIndentIncludingZero = Math.min(minIndentIncludingZero, indent);
                    if (indent > 0) {
                        minIndent = Math.min(minIndent, indent);
                        hasIndentedLines = true;
                    }
                }
            }

            // Use the minimum indentation of indented lines if any exist, otherwise use 0
            int indentToRemove = hasIndentedLines ? minIndent
                    : (minIndentIncludingZero == Integer.MAX_VALUE ? 0 : minIndentIncludingZero);

            // Remove minimum indentation from all lines
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    result.append("\n");
                } else {
                    // Ensure we don't go beyond the line length
                    int indentToRemoveForLine = Math.min(indentToRemove, line.length());
                    result.append(line.substring(indentToRemoveForLine)).append("\n");
                }
            }

            // Trim only trailing whitespace/newlines, preserve leading structure
            String resultStr = result.toString();
            // Remove trailing newlines and whitespace
            while (resultStr.endsWith("\n") || resultStr.endsWith(" ") || resultStr.endsWith("\r")) {
                resultStr = resultStr.substring(0, resultStr.length() - 1);
            }
            return Optional.of(resultStr);
        } catch (IOException e) {
            LOG.error("Error reading source file: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Updates the README file by replacing code snippets between HTML comment
     * markers.
     *
     * @param readmePath path to the README.md file
     * @param sourceFile path to the ReadmeContentManager.java file
     * @return true if update was successful
     */
    public static boolean updateReadmeSnippets(Path readmePath, Path sourceFile) {
        try {
            String readmeContent = Files.readString(readmePath, StandardCharsets.UTF_8);
            String[] snippetIds = { "ema-crossover", "rsi-strategy", "strategy-performance", "advanced-strategy",
                    "serialize-indicator", "serialize-rule", "serialize-strategy" };

            boolean updated = false;
            for (String snippetId : snippetIds) {
                Optional<String> snippet = extractCodeSnippet(sourceFile, snippetId);
                if (snippet.isEmpty()) {
                    LOG.warn("Could not extract snippet: {}", snippetId);
                    continue;
                }

                String startMarker = "<!-- START_SNIPPET: " + snippetId + " -->";
                String endMarker = "<!-- END_SNIPPET: " + snippetId + " -->";

                // Find the code block between markers
                Pattern pattern = Pattern.compile(Pattern.quote(startMarker) + ".*?" + Pattern.quote(endMarker),
                        Pattern.DOTALL);

                String replacement = startMarker + "\n```java\n" + snippet.get() + "\n```\n" + endMarker;
                String before = readmeContent;
                readmeContent = pattern.matcher(readmeContent).replaceAll(Matcher.quoteReplacement(replacement));

                if (!readmeContent.equals(before)) {
                    updated = true;
                    LOG.info("Updated snippet in README: {}", snippetId);
                } else {
                    LOG.warn("Snippet markers not found in README for: {}", snippetId);
                }
            }

            if (updated) {
                Files.writeString(readmePath, readmeContent, StandardCharsets.UTF_8);
                LOG.info("README updated successfully");
                return true;
            } else {
                LOG.warn("No updates made to README");
                return false;
            }
        } catch (IOException e) {
            LOG.error("Error updating README: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Main method to generate all README charts and automatically update README
     * code snippets.
     *
     * <p>
     * By default, this method generates all chart images and automatically updates
     * the README with code snippets extracted from the chart generation methods to
     * keep them in sync.
     * </p>
     *
     * @param args optional arguments: - "update-readme": Only updates README.md
     *             with code snippets (no chart generation) - "snippets": Only
     *             prints all code snippets to log (no chart generation) -
     *             Otherwise: output directory for charts (defaults to
     *             "ta4j-examples/docs/img")
     */
    public static void main(String[] args) {
        if (args.length > 0 && "update-readme".equals(args[0])) {
            LOG.info("=== Updating README with code snippets ===");
            Path projectRoot = findProjectRoot();
            Path sourceFile = projectRoot
                    .resolve("ta4j-examples/src/main/java/ta4jexamples/doc/ReadmeContentManager.java")
                    .normalize();
            Path readmePath = projectRoot.resolve("README.md").normalize();

            if (!Files.exists(sourceFile)) {
                LOG.error("Source file not found: {}", sourceFile.toAbsolutePath());
                return;
            }
            if (!Files.exists(readmePath)) {
                LOG.error("README file not found: {}", readmePath.toAbsolutePath());
                return;
            }

            boolean success = updateReadmeSnippets(readmePath, sourceFile);
            if (success) {
                LOG.info("=== README update complete ===");
            } else {
                LOG.warn("=== README update failed or no changes made ===");
            }
            return;
        }

        if (args.length > 0 && "snippets".equals(args[0])) {
            LOG.info("=== Extracting code snippets ===");
            Path projectRoot = findProjectRoot();
            Path sourceFile = projectRoot
                    .resolve("ta4j-examples/src/main/java/ta4jexamples/doc/ReadmeContentManager.java")
                    .normalize();
            String[] snippetIds = { "ema-crossover", "rsi-strategy", "strategy-performance", "advanced-strategy",
                    "serialize-indicator", "serialize-rule", "serialize-strategy" };

            for (String snippetId : snippetIds) {
                Optional<String> snippet = extractCodeSnippet(sourceFile, snippetId);
                if (snippet.isPresent()) {
                    LOG.info("\n=== {} Code Snippet ===", snippetId);
                    LOG.info("\n{}", snippet.get());
                } else {
                    LOG.warn("Could not extract snippet: {}", snippetId);
                }
            }
            return;
        }

        // Chart generation requires a display (non-headless environment)
        if (GraphicsEnvironment.isHeadless()) {
            LOG.error("Cannot generate charts in headless environment. Chart generation requires a display.");
            LOG.error("Use 'update-readme' or 'snippets' arguments for headless operations.");
            System.exit(1);
        }

        String outputDir = args.length > 0 ? args[0] : "ta4j-examples/docs/img";

        LOG.info("=== README Content Manager ===");
        LOG.info("Output directory: {}", outputDir);

        // Generate all charts
        generateEmaCrossoverChart(outputDir);
        generateRsiStrategyChart(outputDir);
        generateStrategyPerformanceChart(outputDir);
        generateAdvancedStrategyChart(outputDir);

        LOG.info("=== Chart generation complete ===");

        // Automatically update README with code snippets to keep them in sync
        LOG.info("=== Updating README with code snippets ===");
        Path projectRoot = findProjectRoot();
        Path sourceFile = projectRoot.resolve("ta4j-examples/src/main/java/ta4jexamples/doc/ReadmeContentManager.java")
                .normalize();
        Path readmePath = projectRoot.resolve("README.md").normalize();

        if (Files.exists(sourceFile) && Files.exists(readmePath)) {
            boolean success = updateReadmeSnippets(readmePath, sourceFile);
            if (success) {
                LOG.info("=== README updated successfully ===");
            } else {
                LOG.warn("=== README update completed (no changes or warnings occurred) ===");
            }
        } else {
            LOG.warn("Could not update README: source file or README not found");
            LOG.warn("Source: {}, exists: {}", sourceFile.toAbsolutePath(), Files.exists(sourceFile));
            LOG.warn("README: {}, exists: {}", readmePath.toAbsolutePath(), Files.exists(readmePath));
        }

        LOG.info("To view code snippets only, run with argument 'snippets'");
    }
}
