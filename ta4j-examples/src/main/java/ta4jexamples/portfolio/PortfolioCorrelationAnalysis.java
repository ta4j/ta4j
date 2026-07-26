/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.PortfolioCorrelations;
import org.ta4j.core.portfolio.PortfolioCorrelations.ClusterMerge;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationHierarchy;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationMatrix;
import org.ta4j.core.portfolio.PortfolioSeries;

import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource.YahooFinanceInterval;

/**
 * Runs price and return correlation analysis over several anonymous sample
 * universes.
 *
 * <p>
 * Daily adjusted Yahoo bars are normalized to UTC calendar dates before the
 * aligned {@link PortfolioSeries} is constructed. This lets mixed exchange
 * schedules share a strict common timeline while keeping every preset free of
 * account or owner identifiers.
 * </p>
 *
 * @since 0.23.1
 */
public final class PortfolioCorrelationAnalysis {

    private static final int MATRIX_WIDTH = 9;
    private static final int SYMBOL_WIDTH = 11;

    private PortfolioCorrelationAnalysis() {
    }

    public static void main(String[] args) {
        PortfolioPreset preset = args.length > 0 ? PortfolioPreset.from(args[0]) : PortfolioPreset.INDEX;
        AnalysisKind requestedKind = args.length > 1 ? AnalysisKind.from(args[1]) : null;

        if (preset == null || (args.length > 0 && isHelp(args[0]))) {
            printUsage();
            return;
        }

        System.out.printf("Portfolio correlation analysis: %s%n", preset.title());
        System.out.printf("Tickers: %s%n%n", String.join(", ", preset.tickerNames().keySet()));

        YahooFinanceHttpBarSeriesDataSource dataSource = new YahooFinanceHttpBarSeriesDataSource(true);
        for (AnalysisSpec analysis : preset.analyses(requestedKind)) {
            runAnalysis(dataSource, preset, analysis);
        }
    }

    private static void runAnalysis(YahooFinanceHttpBarSeriesDataSource dataSource, PortfolioPreset preset,
            AnalysisSpec analysis) {
        System.out.printf("=== %s, period=%s ===%n", analysis.kind().label(), analysis.period().label());
        PortfolioSeries series = loadPortfolioSeries(dataSource, preset, analysis.period());
        CorrelationMatrix matrix = analysis.kind().matrix(series);
        System.out.printf("Aligned daily bars: %d (%s to %s)%n", series.getBarCount(), series.getEndTimes().getFirst(),
                series.getEndTimes().getLast());
        printMatrix(matrix, preset.tickerNames());
        printPairReport(matrix, preset.tickerNames());
        printCompleteLinkage(matrix, preset.tickerNames());
        System.out.println();
    }

    private static PortfolioSeries loadPortfolioSeries(YahooFinanceHttpBarSeriesDataSource dataSource,
            PortfolioPreset preset, PeriodSpec period) {
        Instant end = Instant.now();
        Instant start = period.start(end);
        List<BarSeries> assetSeries = new ArrayList<>();
        for (String ticker : preset.tickerNames().keySet()) {
            BarSeries rawSeries = dataSource.loadAdjustedSeriesInstance(ticker, YahooFinanceInterval.DAY_1, start, end);
            if (rawSeries == null || rawSeries.isEmpty()) {
                throw new IllegalStateException("No Yahoo Finance data returned for " + ticker);
            }
            assetSeries.add(normalizeDailySeries(ticker, rawSeries));
        }
        return new PortfolioSeries(assetSeries);
    }

    static BarSeries normalizeDailySeries(String ticker, BarSeries rawSeries) {
        TreeMap<LocalDate, Bar> barsByDate = new TreeMap<>();
        for (int index = rawSeries.getBeginIndex(); index <= rawSeries.getEndIndex(); index++) {
            Bar bar = rawSeries.getBar(index);
            barsByDate.put(LocalDate.ofInstant(bar.getEndTime(), ZoneOffset.UTC), bar);
        }

        BarSeries normalized = new BaseBarSeriesBuilder().withName(ticker)
                .withNumFactory(rawSeries.numFactory())
                .build();
        for (Map.Entry<LocalDate, Bar> entry : barsByDate.entrySet()) {
            Bar bar = entry.getValue();
            normalized.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(entry.getKey().atStartOfDay().toInstant(ZoneOffset.UTC))
                    .openPrice(bar.getOpenPrice())
                    .highPrice(bar.getHighPrice())
                    .lowPrice(bar.getLowPrice())
                    .closePrice(bar.getClosePrice())
                    .volume(bar.getVolume())
                    .amount(bar.getAmount())
                    .add();
        }
        return normalized;
    }

    private static void printMatrix(CorrelationMatrix matrix, Map<String, String> labels) {
        System.out.println("Correlation matrix:");
        System.out.printf("%" + SYMBOL_WIDTH + "s", "");
        for (String asset : matrix.getAssets()) {
            System.out.printf(" %" + MATRIX_WIDTH + "s", asset);
        }
        System.out.println();
        for (String rowAsset : matrix.getAssets()) {
            System.out.printf("%" + SYMBOL_WIDTH + "s", rowAsset);
            for (String columnAsset : matrix.getAssets()) {
                System.out.printf(" %" + MATRIX_WIDTH + ".4f",
                        matrix.getCoefficient(rowAsset, columnAsset).doubleValue());
            }
            System.out.printf("  %s%n", labels.get(rowAsset));
        }
    }

    private static void printPairReport(CorrelationMatrix matrix, Map<String, String> labels) {
        System.out.println("Pair report:");
        for (PortfolioCorrelations.CorrelationPair pair : matrix.getPairs()) {
            System.out.printf("  %s / %s: %.4f%n", labels.get(pair.getFirstAsset()), labels.get(pair.getSecondAsset()),
                    pair.getCoefficient().doubleValue());
        }
    }

    private static void printCompleteLinkage(CorrelationMatrix matrix, Map<String, String> labels) {
        try {
            CorrelationHierarchy hierarchy = matrix.completeLinkage();
            Map<Integer, String> clusterLabels = new LinkedHashMap<>();
            for (int index = 0; index < hierarchy.getAssets().size(); index++) {
                String asset = hierarchy.getAssets().get(index);
                clusterLabels.put(index, labels.get(asset));
            }
            System.out.println("Complete-linkage clusters:");
            for (int index = 0; index < hierarchy.getMerges().size(); index++) {
                ClusterMerge merge = hierarchy.getMerges().get(index);
                String left = clusterLabels.get(merge.getLeftClusterIndex());
                String right = clusterLabels.get(merge.getRightClusterIndex());
                System.out.printf("  %2d. %s + %s distance=%.4f size=%d%n", index + 1, left, right,
                        merge.getDistance().doubleValue(), merge.getSize());
                clusterLabels.put(hierarchy.getAssets().size() + index, left + " + " + right);
            }
        } catch (IllegalStateException exception) {
            System.out.println("Complete-linkage clusters: skipped because the matrix contains non-finite values");
        }
    }

    private static boolean isHelp(String value) {
        return "-h".equals(value) || "--help".equals(value) || "help".equalsIgnoreCase(value);
    }

    private static void printUsage() {
        System.out.println("Usage: PortfolioCorrelationAnalysis [preset] [price|simple-return|log-return]");
        System.out.println("Presets:");
        for (PortfolioPreset preset : PortfolioPreset.values()) {
            System.out.printf("  %-28s %s%n", preset.id(), preset.title());
        }
    }

    private static String normalizeToken(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private enum AnalysisKind {
        PRICE("price") {
            @Override
            CorrelationMatrix matrix(PortfolioSeries series) {
                return new PortfolioCorrelations(series).getPriceMatrix();
            }
        },
        SIMPLE_RETURN("simple-return") {
            @Override
            CorrelationMatrix matrix(PortfolioSeries series) {
                return new PortfolioCorrelations(series).getSimpleReturnMatrix();
            }
        },
        LOG_RETURN("log-return") {
            @Override
            CorrelationMatrix matrix(PortfolioSeries series) {
                return new PortfolioCorrelations(series).getLogReturnMatrix();
            }
        };

        private final String label;

        AnalysisKind(String label) {
            this.label = label;
        }

        abstract CorrelationMatrix matrix(PortfolioSeries series);

        String label() {
            return label;
        }

        static AnalysisKind from(String value) {
            String normalized = normalizeToken(value);
            return Arrays.stream(values())
                    .filter(kind -> kind.label.equals(normalized))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown analysis kind: " + value));
        }
    }

    private enum PeriodSpec {
        ONE_MONTH("1mo"), THREE_MONTHS("3mo"), SIX_MONTHS("6mo"), ONE_YEAR("1y"), FIVE_YEARS("5y"), YEAR_TO_DATE("ytd"),
        MAX("max");

        private final String label;

        PeriodSpec(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        Instant start(Instant end) {
            return switch (this) {
            case ONE_MONTH -> end.minus(Duration.ofDays(31));
            case THREE_MONTHS -> end.minus(Duration.ofDays(93));
            case SIX_MONTHS -> end.minus(Duration.ofDays(186));
            case ONE_YEAR -> end.minus(Duration.ofDays(366));
            case FIVE_YEARS -> end.minus(Duration.ofDays(5L * 366));
            case YEAR_TO_DATE ->
                LocalDate.of(Year.now(ZoneOffset.UTC).getValue(), 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
            case MAX -> Instant.parse("1900-01-01T00:00:00Z");
            };
        }
    }

    private enum PortfolioPreset {
        DIVERSIFIED_STOCKS_BONDS("diversified-stocks-bonds", "Diversified stocks and bonds",
                tickers("AAP", "AAPL", "BABA", "COIN", "FBT", "FBTC", "GXO", "HOOD", "IBIT", "IJH", "INTC", "JPM",
                        "JWN", "MCHI", "MED", "PYPL", "RSP", "SWK", "TLT", "VB", "VGIT", "VWO"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.MAX))),
        BALANCED_ETFS("balanced-etfs", "Balanced growth and income ETFs",
                tickers("VIGAX", "FDRR", "SPY", "QUS", "VBR", "AKREX", "BIV", "ICLN"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.YEAR_TO_DATE))),
        DIGITAL_ASSET_TILT("digital-asset-tilt", "Diversified portfolio with a digital-asset tilt",
                tickers("QQQ", "VWO", "COIN", "FBTC", "IBIT", "ETHW", "RES", "XOM", "DOC", "NKE", "ARKK", "TLT"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.YEAR_TO_DATE))),
        LARGE_CAP_INCOME("large-cap-income", "Large-cap equity and income mix",
                tickers("BABA", "CSCO", "GOOG", "INTC", "JPM", "MMM", "MSFT", "PFE", "PYPL", "RES", "SOLV", "TLT",
                        "TMUS", "VNQ", "XOM"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.MAX))),
        INDEX("index", "Index, futures, crypto, and treasury-yield mix",
                tickers("BTC-USD", "ETH-USD", "^GSPC", "^IXIC", "^DJI", "^RUT", "GC=F", "QI=F", "CL=F", "^TNX", "^FVX",
                        "^FTSE", "^N225", "^VIX", "HG=F"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.ONE_MONTH))),
        PAIR("pair", "BTC, bonds, gold, and Monero pair study", tickers("BTC-USD", "TLT", "GC=F", "XMR-USD"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.FIVE_YEARS))),
        CRYPTO("crypto", "Crypto asset universe",
                tickers("BTC-USD", "ETH-USD", "XRP-USD", "ADA-USD", "DOGE-USD", "SOL-USD", "MATIC-USD", "DOT-USD",
                        "ZEC-USD", "BNB-USD", "HEX-USD", "LTC-USD", "SHIB-USD", "AVAX-USD", "UNI7083-USD", "LINK-USD",
                        "ATOM-USD", "XNO-USD", "XMR-USD", "AAVE-USD", "SNX-USD", "CFX-USD", "BAT-USD", "COMP5692-USD",
                        "METIS-USD", "VRSC-USD", "PREMIA-USD", "APT-USD", "FIL-USD", "MKR-USD"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.MAX))),
        MARKET_PRICE_OVER_TIME("market-price-over-time", "Asset price correlations over multiple periods",
                friendly("BTC-USD", "BTC-USD", "ETH-USD", "ETH-USD", "^GSPC", "S&P 500", "^IXIC", "NASDAQ", "^DJI",
                        "Dow Jones", "^RUT", "Russell 2000", "GC=F", "Gold", "QI=F", "Silver", "CL=F", "Crude Oil",
                        "HG=F", "Copper", "^TNX", "10 Year Treasury Yield", "^FVX", "5 Year Treasury Yield", "VWO",
                        "VWO"),
                periods(AnalysisKind.PRICE, PeriodSpec.ONE_MONTH, PeriodSpec.THREE_MONTHS, PeriodSpec.SIX_MONTHS,
                        PeriodSpec.ONE_YEAR, PeriodSpec.MAX)),
        MARKET_PRICE_CHANGE_OVER_TIME("market-price-change-over-time",
                "Asset price-change correlations over multiple periods",
                friendly("BTC-USD", "BTC-USD", "ETH-USD", "ETH-USD", "^GSPC", "S&P 500", "RSP", "S&P 500 Equal Weight",
                        "^IXIC", "NASDAQ", "^DJI", "Dow Jones", "^RUT", "Russell 2000", "GC=F", "Gold", "QI=F",
                        "Silver", "CL=F", "Crude Oil", "HG=F", "Copper", "^TNX", "10Y Treasury Yield", "^FVX",
                        "5Y Treasury Yield", "VWO", "VWO"),
                periods(AnalysisKind.SIMPLE_RETURN, PeriodSpec.ONE_MONTH, PeriodSpec.THREE_MONTHS,
                        PeriodSpec.SIX_MONTHS, PeriodSpec.ONE_YEAR, PeriodSpec.MAX));

        private final String id;
        private final String title;
        private final Map<String, String> tickerNames;
        private final List<AnalysisSpec> analyses;

        PortfolioPreset(String id, String title, Map<String, String> tickerNames, List<AnalysisSpec> analyses) {
            this.id = id;
            this.title = title;
            this.tickerNames = tickerNames;
            this.analyses = analyses;
        }

        String id() {
            return id;
        }

        String title() {
            return title;
        }

        Map<String, String> tickerNames() {
            return tickerNames;
        }

        List<AnalysisSpec> analyses(AnalysisKind requestedKind) {
            if (requestedKind == null) {
                return analyses;
            }
            List<AnalysisSpec> requestedAnalyses = new ArrayList<>(analyses.size());
            for (AnalysisSpec analysis : analyses) {
                requestedAnalyses.add(new AnalysisSpec(requestedKind, analysis.period()));
            }
            return List.copyOf(requestedAnalyses);
        }

        static PortfolioPreset from(String value) {
            if (isHelp(value)) {
                return null;
            }
            String normalized = normalizeToken(value);
            return Arrays.stream(values()).filter(preset -> preset.id.equals(normalized)).findFirst().orElse(null);
        }
    }

    private static Map<String, String> tickers(String... tickers) {
        Map<String, String> tickerNames = new LinkedHashMap<>();
        for (String ticker : tickers) {
            tickerNames.put(ticker, ticker);
        }
        return Collections.unmodifiableMap(tickerNames);
    }

    private static Map<String, String> friendly(String... tickerNamePairs) {
        if (tickerNamePairs.length % 2 != 0) {
            throw new IllegalArgumentException("tickerNamePairs must contain ticker/name pairs");
        }
        Map<String, String> tickerNames = new LinkedHashMap<>();
        for (int index = 0; index < tickerNamePairs.length; index += 2) {
            tickerNames.put(tickerNamePairs[index], tickerNamePairs[index + 1]);
        }
        return Collections.unmodifiableMap(tickerNames);
    }

    private static List<AnalysisSpec> periods(AnalysisKind kind, PeriodSpec... periods) {
        return Arrays.stream(periods).map(period -> new AnalysisSpec(kind, period)).toList();
    }

    private record AnalysisSpec(AnalysisKind kind, PeriodSpec period) {
    }
}
