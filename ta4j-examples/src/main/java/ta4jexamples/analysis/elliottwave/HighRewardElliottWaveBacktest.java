/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.PositionsRatioCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitLossRatioCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.num.Num;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;
import ta4jexamples.strategies.HighRewardElliottWaveStrategy;

/**
 * Backtests the high-reward Elliott Wave strategy using ossified datasets.
 *
 * @since 0.22.2
 */
public class HighRewardElliottWaveBacktest {

    private static final Logger LOG = LogManager.getLogger(HighRewardElliottWaveBacktest.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final String DEFAULT_OHLCV_RESOURCE = "Coinbase-ETH-USD-PT1D-20160517_20251028.json";

    private static final List<StrategySpec> DEFAULT_SPECS = List.of(StrategySpec.defaultSpec(),
            StrategySpec.relaxedSpec(), StrategySpec.exploratorySpec(), StrategySpec.strictSpec());

    /**
     * Runs the backtest demo.
     *
     * @param args command-line arguments (optional: override dataset resource)
     */
    public static void main(String[] args) {
        String resource = args.length > 0 ? args[0] : DEFAULT_OHLCV_RESOURCE;
        BarSeries series = loadSeries(resource);
        if (series == null || series.isEmpty()) {
            LOG.error("No data available for backtest: {}", resource);
            return;
        }

        LOG.info("High-reward Elliott Wave backtest: {}", resource);
        LOG.info("Bars: {} | Range: {}", series.getBarCount(), describeRange(series));

        for (StrategySpec spec : DEFAULT_SPECS) {
            runBacktest(series, spec);
        }
    }

    private static void runBacktest(BarSeries series, StrategySpec spec) {
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, spec.toParameters());
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        Num netProfit = new NetProfitLossCriterion().calculate(series, record);
        Num grossReturn = new GrossReturnCriterion().calculate(series, record);
        Num profitFactor = new GrossProfitLossRatioCriterion().calculate(series, record);
        Num maxDrawdown = new MaximumDrawdownCriterion().calculate(series, record);
        Num winRate = PositionsRatioCriterion.WinningPositionsRatioCriterion().calculate(series, record);
        Num expectancy = new ExpectancyCriterion().calculate(series, record);

        LOG.info("Spec: {}", spec.label());
        LOG.info(
                "Trades: {} | WinRate: {} | Net PnL: {} | Gross Return: {} | Profit Factor: {} | Max DD: {} | Expectancy: {}",
                record.getPositionCount(), formatPercent(winRate), netProfit, grossReturn, profitFactor, maxDrawdown,
                expectancy);
        logTrades(series, record);
    }

    private static String formatPercent(Num ratio) {
        if (ratio == null || ratio.isNaN()) {
            return "n/a";
        }
        return String.format("%.1f%%", ratio.doubleValue() * 100.0);
    }

    private static BarSeries loadSeries(String resource) {
        try (InputStream stream = HighRewardElliottWaveBacktest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                LOG.error("Missing resource: {}", resource);
                return null;
            }
            BarSeries loaded = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
            if (loaded == null) {
                LOG.error("Failed to load resource: {}", resource);
                return null;
            }
            BarSeries series = new BaseBarSeriesBuilder().withName(resource).build();
            for (int i = 0; i < loaded.getBarCount(); i++) {
                series.addBar(loaded.getBar(i));
            }
            return series;
        } catch (Exception ex) {
            LOG.error("Failed to load dataset: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private static String describeRange(BarSeries series) {
        String start = DATE_FORMATTER.format(series.getBar(series.getBeginIndex()).getEndTime());
        String end = DATE_FORMATTER.format(series.getBar(series.getEndIndex()).getEndTime());
        return start + " -> " + end;
    }

    private static void logTrades(BarSeries series, TradingRecord record) {
        if (record == null || record.getPositionCount() == 0) {
            return;
        }
        record.getPositions().stream().filter(position -> !position.isOpened()).forEach(position -> {
            Trade entry = position.getEntry();
            Trade exit = position.getExit();
            String entryTime = DATE_FORMATTER.format(series.getBar(entry.getIndex()).getEndTime());
            String exitTime = DATE_FORMATTER.format(series.getBar(exit.getIndex()).getEndTime());
            LOG.info("Trade: entry={}@{} ({}), exit={}@{} ({}), profit={}", entry.getIndex(), entry.getPricePerAsset(),
                    entryTime, exit.getIndex(), exit.getPricePerAsset(), exitTime, position.getProfit());
        });
    }

    private record StrategySpec(String direction, String degree, double minConfidence, double minRiskReward,
            double minAlternationRatio, double minTrendBiasStrength, int trendSmaPeriod, int rsiPeriod,
            double rsiThreshold, int macdFastPeriod, int macdSlowPeriod, double minRelativeSwing) {

        static StrategySpec defaultSpec() {
            return new StrategySpec("BULLISH", "PRIMARY", 0.35, 2.0, 1.50, 0.10, 100, 14, 50.0, 12, 26, 0.10);
        }

        static StrategySpec relaxedSpec() {
            return new StrategySpec("BULLISH", "MINOR", 0.30, 1.8, 1.05, 0.05, 80, 14, 48.0, 12, 26, 0.08);
        }

        static StrategySpec exploratorySpec() {
            return new StrategySpec("BULLISH", "MINOR", 0.25, 1.5, 1.00, 0.00, 80, 14, 45.0, 12, 26, 0.05);
        }

        static StrategySpec strictSpec() {
            return new StrategySpec("BULLISH", "PRIMARY", 0.50, 2.5, 1.40, 0.20, 200, 14, 50.0, 12, 26, 0.15);
        }

        String[] toParameters() {
            return new String[] { direction, degree, format(minConfidence), format(minRiskReward),
                    format(minAlternationRatio), format(minTrendBiasStrength), String.valueOf(trendSmaPeriod),
                    String.valueOf(rsiPeriod), format(rsiThreshold), String.valueOf(macdFastPeriod),
                    String.valueOf(macdSlowPeriod), format(minRelativeSwing) };
        }

        String label() {
            return String.format("dir=%s deg=%s conf=%s rr=%s alt=%s bias=%s swing=%s", direction, degree,
                    format(minConfidence), format(minRiskReward), format(minAlternationRatio),
                    format(minTrendBiasStrength), format(minRelativeSwing));
        }

        private static String format(double value) {
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
    }
}
