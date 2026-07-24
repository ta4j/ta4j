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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.AlignedPortfolioSeries;
import org.ta4j.core.portfolio.PortfolioAsset;
import org.ta4j.core.portfolio.PortfolioCorrelations;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationMatrix;
import org.ta4j.core.portfolio.PortfolioSeries;

import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource.YahooFinanceInterval;

/**
 * Recreates the AssetCorrelations Python project with ta4j portfolio plumbing.
 *
 * <p>
 * The sample universes are the ticker lists from the Python scripts. Daily bars
 * are normalized to UTC calendar dates before building an
 * {@link AlignedPortfolioSeries}, so mixed assets such as crypto, futures,
 * equities, ETFs, and indexes can share the portfolio feature's strict common
 * timeline.
 * </p>
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
        AlignedPortfolioSeries series = loadPortfolioSeries(dataSource, preset, analysis.period());
        CorrelationMatrix matrix = analysis.kind().matrix(series);
        System.out.printf("Aligned daily bars: %d (%s to %s)%n", series.getBarCount(), series.endTimes().getFirst(),
                series.endTimes().getLast());
        printMatrix(matrix, preset.assetsByTicker());
        printPairReport(matrix, preset.assetsByTicker());
        printCompleteLinkage(matrix, preset.assetsByTicker());
        System.out.println();
    }

    private static AlignedPortfolioSeries loadPortfolioSeries(YahooFinanceHttpBarSeriesDataSource dataSource,
            PortfolioPreset preset, PeriodSpec period) {
        Instant end = Instant.now();
        Instant start = period.start(end);
        List<PortfolioSeries> portfolioSeries = new ArrayList<>();
        for (String ticker : preset.tickerNames().keySet()) {
            BarSeries rawSeries = dataSource.loadSeriesInstance(ticker, YahooFinanceInterval.DAY_1, start, end,
                    preset.id() + "-" + period.label());
            if (rawSeries == null || rawSeries.isEmpty()) {
                throw new IllegalStateException("No Yahoo Finance data returned for " + ticker);
            }
            portfolioSeries.add(PortfolioSeries.of(ticker, normalizeDailyCloseSeries(ticker, rawSeries)));
        }
        return AlignedPortfolioSeries.of(portfolioSeries);
    }

    private static BarSeries normalizeDailyCloseSeries(String ticker, BarSeries rawSeries) {
        TreeMap<LocalDate, Bar> barsByDate = new TreeMap<>();
        for (int index = rawSeries.getBeginIndex(); index <= rawSeries.getEndIndex(); index++) {
            Bar bar = rawSeries.getBar(index);
            LocalDate date = LocalDate.ofInstant(bar.getEndTime(), ZoneOffset.UTC);
            barsByDate.put(date, bar);
        }

        BarSeries normalized = new BaseBarSeriesBuilder().withName(ticker)
                .withNumFactory(rawSeries.numFactory())
                .build();
        Num zero = rawSeries.numFactory().zero();
        for (Map.Entry<LocalDate, Bar> entry : barsByDate.entrySet()) {
            Num close = entry.getValue().getClosePrice();
            normalized.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(entry.getKey().atStartOfDay().toInstant(ZoneOffset.UTC))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(zero)
                    .add();
        }
        return normalized;
    }

    private static void printMatrix(CorrelationMatrix matrix, Map<PortfolioAsset, String> labels) {
        System.out.println("Correlation matrix:");
        System.out.printf("%" + SYMBOL_WIDTH + "s", "");
        for (PortfolioAsset asset : matrix.assets()) {
            System.out.printf(" %" + MATRIX_WIDTH + "s", asset.id());
        }
        System.out.println();
        for (PortfolioAsset rowAsset : matrix.assets()) {
            System.out.printf("%" + SYMBOL_WIDTH + "s", rowAsset.id());
            for (PortfolioAsset columnAsset : matrix.assets()) {
                System.out.printf(" %" + MATRIX_WIDTH + ".4f", matrix.coefficient(rowAsset, columnAsset).doubleValue());
            }
            System.out.printf("  %s%n", labels.get(rowAsset));
        }
    }

    private static void printPairReport(CorrelationMatrix matrix, Map<PortfolioAsset, String> labels) {
        System.out.println("Pair report:");
        for (PortfolioCorrelations.CorrelationPair pair : matrix.pairs()) {
            System.out.printf("  %s / %s: %.4f%n", labels.get(pair.firstAsset()), labels.get(pair.secondAsset()),
                    pair.coefficient().doubleValue());
        }
    }

    private static void printCompleteLinkage(CorrelationMatrix matrix, Map<PortfolioAsset, String> labels) {
        List<ClusterMerge> merges = completeLinkage(matrix, labels);
        if (merges.isEmpty()) {
            System.out.println("Complete-linkage clusters: skipped because the matrix contains non-finite values");
            return;
        }
        System.out.println("Complete-linkage clusters:");
        for (int index = 0; index < merges.size(); index++) {
            ClusterMerge merge = merges.get(index);
            System.out.printf("  %2d. %s + %s distance=%.4f size=%d%n", index + 1, merge.left(), merge.right(),
                    merge.distance(), merge.size());
        }
    }

    private static List<ClusterMerge> completeLinkage(CorrelationMatrix matrix, Map<PortfolioAsset, String> labels) {
        List<PortfolioAsset> assets = matrix.assets();
        double[][] rows = new double[assets.size()][assets.size()];
        for (int rowIndex = 0; rowIndex < assets.size(); rowIndex++) {
            for (int columnIndex = 0; columnIndex < assets.size(); columnIndex++) {
                double value = matrix.coefficient(assets.get(rowIndex), assets.get(columnIndex)).doubleValue();
                if (!Double.isFinite(value)) {
                    return List.of();
                }
                rows[rowIndex][columnIndex] = value;
            }
        }

        List<Cluster> clusters = new ArrayList<>();
        for (int index = 0; index < assets.size(); index++) {
            clusters.add(new Cluster(labels.get(assets.get(index)), List.of(index)));
        }

        List<ClusterMerge> merges = new ArrayList<>();
        while (clusters.size() > 1) {
            ClusterPair closest = closestPair(clusters, rows);
            Cluster left = clusters.get(closest.leftIndex());
            Cluster right = clusters.get(closest.rightIndex());
            List<Integer> mergedMembers = new ArrayList<>(left.members());
            mergedMembers.addAll(right.members());
            Cluster merged = new Cluster(left.label() + " + " + right.label(), List.copyOf(mergedMembers));
            merges.add(new ClusterMerge(left.label(), right.label(), closest.distance(), mergedMembers.size()));
            clusters.remove(closest.rightIndex());
            clusters.remove(closest.leftIndex());
            clusters.add(merged);
        }
        return List.copyOf(merges);
    }

    private static ClusterPair closestPair(List<Cluster> clusters, double[][] rows) {
        ClusterPair closest = null;
        for (int leftIndex = 0; leftIndex < clusters.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < clusters.size(); rightIndex++) {
                double distance = completeDistance(clusters.get(leftIndex), clusters.get(rightIndex), rows);
                ClusterPair candidate = new ClusterPair(leftIndex, rightIndex, distance);
                if (closest == null || candidate.compareTo(closest) < 0) {
                    closest = candidate;
                }
            }
        }
        return Objects.requireNonNull(closest, "closest");
    }

    private static double completeDistance(Cluster left, Cluster right, double[][] rows) {
        double distance = 0.0;
        for (int leftMember : left.members()) {
            for (int rightMember : right.members()) {
                distance = Math.max(distance, euclideanDistance(rows[leftMember], rows[rightMember]));
            }
        }
        return distance;
    }

    private static double euclideanDistance(double[] left, double[] right) {
        double squaredDistance = 0.0;
        for (int index = 0; index < left.length; index++) {
            double difference = left[index] - right[index];
            squaredDistance += difference * difference;
        }
        return Math.sqrt(squaredDistance);
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
            CorrelationMatrix matrix(AlignedPortfolioSeries series) {
                return PortfolioCorrelations.priceMatrix(series);
            }
        },
        SIMPLE_RETURN("simple-return") {
            @Override
            CorrelationMatrix matrix(AlignedPortfolioSeries series) {
                return PortfolioCorrelations.simpleReturnMatrix(series);
            }
        },
        LOG_RETURN("log-return") {
            @Override
            CorrelationMatrix matrix(AlignedPortfolioSeries series) {
                return PortfolioCorrelations.logReturnMatrix(series);
            }
        };

        private final String label;

        AnalysisKind(String label) {
            this.label = label;
        }

        abstract CorrelationMatrix matrix(AlignedPortfolioSeries series);

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
        MARY_TRADITIONAL_IRA("mary-traditional-ira", "Mary traditional IRA",
                tickers("AAP", "AAPL", "BABA", "COIN", "FBT", "FBTC", "GXO", "HOOD", "IBIT", "IJH", "INTC", "JPM",
                        "JWN", "MCHI", "MED", "PYPL", "RSP", "SWK", "TLT", "VB", "VGIT", "VWO"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.MAX))),
        ADELA_ROTH_IRA("adela-roth-ira", "Adela Roth IRA",
                tickers("VIGAX", "FDRR", "SPY", "QUS", "VBR", "AKREX", "BIV", "ICLN"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.YEAR_TO_DATE))),
        NINI_ROTH_IRA("nini-roth-ira", "Nini Roth IRA",
                tickers("QQQ", "VWO", "COIN", "FBTC", "IBIT", "ETHW", "RES", "XOM", "DOC", "NKE", "ARKK", "TLT"),
                List.of(new AnalysisSpec(AnalysisKind.PRICE, PeriodSpec.YEAR_TO_DATE))),
        MARY_ROTH_IRA_8637("mary-roth-ira-8637", "Mary Roth IRA 8637",
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

        Map<PortfolioAsset, String> assetsByTicker() {
            Map<PortfolioAsset, String> assetsByTicker = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : tickerNames.entrySet()) {
                assetsByTicker.put(PortfolioAsset.of(entry.getKey()), entry.getValue());
            }
            return Collections.unmodifiableMap(assetsByTicker);
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

    private record Cluster(String label, List<Integer> members) {
    }

    private record ClusterMerge(String left, String right, double distance, int size) {
    }

    private record ClusterPair(int leftIndex, int rightIndex, double distance) implements Comparable<ClusterPair> {

        @Override
        public int compareTo(ClusterPair other) {
            return Comparator.comparingDouble(ClusterPair::distance)
                    .thenComparingInt(ClusterPair::leftIndex)
                    .thenComparingInt(ClusterPair::rightIndex)
                    .compare(this, other);
        }
    }
}
