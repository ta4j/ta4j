package org.ta4j.core.utils;

import org.ta4j.core.*;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.Returns;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.volume.*;
import org.ta4j.core.indicators.statistics.*;
import org.ta4j.core.indicators.pivotpoints.*;
import org.ta4j.core.indicators.keltner.*;
import org.ta4j.core.indicators.ichimoku.*;
import org.ta4j.core.indicators.candles.*;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.adx.*;
import org.ta4j.core.num.*;
import org.ta4j.core.reports.*;
import org.ta4j.core.rules.*;
import org.ta4j.core.rules.helper.ChainLink;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * This class is used to reduce the code
 */
public final class Analysis {

    public static final Comparator<Bar> sortBarsByTime = BarSeriesUtils.sortBarsByTime;
    public static final Num NaN = org.ta4j.core.num.NaN.NaN;
    public static final Class<DoubleNum> doubleNumClass = DoubleNum.class;
    public static final Class<DecimalNum> decimalNumClass = DecimalNum.class;

    public static BarSeries aggregateBars(BarSeries barSeries, Duration timePeriod, String aggregatedSeriesName) {
        return BarSeriesUtils.aggregateBars(barSeries, timePeriod, aggregatedSeriesName);
    }

    public static Bar replaceBarIfChanged(BarSeries barSeries, Bar newBar) {
        return BarSeriesUtils.replaceBarIfChanged(barSeries, newBar);
    }

    public static List<ZonedDateTime> findMissingBars(BarSeries barSeries, boolean findOnlyNaNBars) {
        return BarSeriesUtils.findMissingBars(barSeries, findOnlyNaNBars);
    }

    public static BarSeries convertBarSeries(BarSeries barSeries, Function<Number, Num> conversionFunction) {
        return BarSeriesUtils.convertBarSeries(barSeries,conversionFunction);
    }

    public static List<Bar> findOverlappingBars(BarSeries barSeries) {
        return BarSeriesUtils.findOverlappingBars(barSeries);
    }

    public static void addBars(BarSeries barSeries, List<Bar> newBars) {
        BarSeriesUtils.addBars(barSeries, newBars);
    }

    public static List<Bar> sortBars(List<Bar> bars) {
        return BarSeriesUtils.sortBars(bars);
    }

    public static List<Bar> sortBarSeries(BarSeries series) {
        return BarSeriesUtils.sortBarSeries(series);
    }

    public static BarSeriesManager barSeriesManager(BarSeries series) {
        return new BarSeriesManager(series);
    }

    public static BarSeriesManager barSeriesManager(BarSeries barSeries, CostModel transactionCostModel, CostModel holdingCostModel) {
        return new BarSeriesManager(barSeries, transactionCostModel, holdingCostModel);
    }

    public static BaseBarSeriesBuilder baseBarSeriesBuilder() {
        return new BaseBarSeriesBuilder();
    }

    public static Num NaN() {
        return NaN;
    }

    public static Class<DoubleNum> doubleNumClass() {
        return doubleNumClass;
    }

    public static Class<DecimalNum> decimalNumClass() {
        return decimalNumClass;
    }

    public static Num decimalNumOf(String val) {
        return DecimalNum.valueOf(val);
    }

    public static Num decimalNumOf(String val, int precision) {
        return DecimalNum.valueOf(val, precision);
    }

    public static Num decimalNumOf(long val) {
        return DecimalNum.valueOf(val);
    }

    public static Num decimalNumOf(int val) {
        return DecimalNum.valueOf(val);
    }

    public static Num decimalNumOf(short val) {
        return DecimalNum.valueOf(val);
    }

    public static Num decimalNumOf(float val) {
        return DecimalNum.valueOf(val);
    }

    public static Num decimalNumOf(double val) {
        return DecimalNum.valueOf(val);
    }

    public static Num decimalNumOf(DecimalNum val) {
        return DecimalNum.valueOf(val);
    }

    public static Num decimalNumOf(BigDecimal val) {
        return DecimalNum.valueOf(val);
    }

    public static Num decimalNumOf(BigDecimal val, int precision) {
        return DecimalNum.valueOf(val, precision);
    }

    public static Num decimalNumOf(Number val) {
        return DecimalNum.valueOf(val);
    }

    public static Num doubleNumOf(String val) {
        return DoubleNum.valueOf(val);
    }

    public static Num doubleNumOf(int val) {
        return DoubleNum.valueOf(val);
    }

    public static Num doubleNumOf(long val) {
        return DoubleNum.valueOf(val);
    }

    public static Num doubleNumOf(short val) {
        return DoubleNum.valueOf(val);
    }

    public static Num doubleNumOf(double val) {
        return DoubleNum.valueOf(val);
    }

    public static Num doubleNumOf(float val) {
        return DoubleNum.valueOf(val);
    }

    public static Num doubleNumOf(Number val) {
        return DoubleNum.valueOf(val);
    }

    public static Num max(List<Num> nums) {
        return Objects.requireNonNull(nums).stream()
                .reduce(Num::max)
                .orElse(NaN);
    }

    public static Num max(Num... nums) {
        return Arrays.stream(Objects.requireNonNull(nums))
                .reduce(Num::max)
                .orElse(NaN);
    }

    public static Num min(List<Num> nums) {
        return Objects.requireNonNull(nums).stream()
                .reduce(Num::min)
                .orElse(NaN);
    }

    public static Num min(Num... nums) {
        return Arrays.stream(Objects.requireNonNull(nums))
                .reduce(Num::min)
                .orElse(NaN);
    }

    public static Num nz(Num num, Num defaultNum) {
        if (num == null || num.isNaN()) {
            return defaultNum;
        }
        return num;
    }

    public static Num nz(Num num) {
        if (num == null || num.isNaN()) {
            return num.zero();
        }
        return num;
    }

    public static Num nz0(Num num, Num defaultNum) {
        if (num == null || num.isNaN() || num.isZero()) {
            return defaultNum;
        }
        return num;
    }

    public static Num nz0(Num num) {
        if (num == null || num.isNaN() || num.isZero()) {
            return num.zero();
        }
        return num;
    }

    public static OpenPriceIndicator open(BarSeries series) {
        return new OpenPriceIndicator(series);
    }

    public static HighPriceIndicator high(BarSeries series) {
        return new HighPriceIndicator(series);
    }

    public static LowPriceIndicator low(BarSeries series) {
        return new LowPriceIndicator(series);
    }

    public static ClosePriceIndicator close(BarSeries series) {
        return new ClosePriceIndicator(series);
    }

    public static RSIIndicator rsi(BarSeries series) {
        return new RSIIndicator(series);
    }

    public static RSIIndicator rsi(Indicator<Num> indicator, int barCount) {
        return new RSIIndicator(indicator, barCount);
    }

    public static ZLEMAIndicator zlema(Indicator<Num> indicator, int barCount) {
        return new ZLEMAIndicator(indicator, barCount);
    }

    public static WMAIndicator wma(Indicator<Num> indicator, int barCount) {
        return new WMAIndicator(indicator, barCount);
    }

    public static WilliamsRIndicator williamsR(BarSeries series, int barCount) {
        return new WilliamsRIndicator(series, barCount);
    }

    public static UnstableIndicator unstable(Indicator<Num> indicator, int unstablePeriod) {
        return new UnstableIndicator(indicator, unstablePeriod);
    }

    public static UlcerIndexIndicator ulcerIndex(Indicator<Num> indicator, int barCount) {
        return new UlcerIndexIndicator(indicator, barCount);
    }

    public static TripleEMAIndicator tripleEMA(Indicator<Num> indicator, int barCount) {
        return new TripleEMAIndicator(indicator, barCount);
    }

    public static StochasticRSIIndicator stochasticRSI(BarSeries series, int barCount) {
        return new StochasticRSIIndicator(series, barCount);
    }

    public static StochasticRSIIndicator stochasticRSI(Indicator<Num> indicator, int barCount) {
        return new StochasticRSIIndicator(indicator, barCount);
    }

    public static StochasticRSIIndicator stochasticRSI(RSIIndicator rsiIndicator, int barCount) {
        return new StochasticRSIIndicator(rsiIndicator, barCount);
    }

    public static StochasticOscillatorKIndicator stochasticOscillatorK(BarSeries barSeries, int barCount) {
        return new StochasticOscillatorKIndicator(barSeries, barCount);
    }

    public static StochasticOscillatorKIndicator stochasticOscillatorK(Indicator<Num> indicator, int barCount, HighPriceIndicator highPriceIndicator,
                                                                       LowPriceIndicator lowPriceIndicator) {
        return new StochasticOscillatorKIndicator(indicator, barCount, highPriceIndicator, lowPriceIndicator);
    }

    public static StochasticOscillatorDIndicator stochasticOscillatorD(StochasticOscillatorKIndicator k) {
        return new StochasticOscillatorDIndicator(k);
    }

    public static StochasticOscillatorDIndicator stochasticOscillatorD(Indicator<Num> indicator) {
        return new StochasticOscillatorDIndicator(indicator);
    }

    public static SMAIndicator sma(Indicator<Num> indicator, int barCount) {
        return new SMAIndicator(indicator, barCount);
    }

    public static RWILowIndicator rwiLow(BarSeries series, int barCount) {
        return new RWILowIndicator(series, barCount);
    }

    public static RWIHighIndicator rwiHigh(BarSeries series, int barCount) {
        return new RWIHighIndicator(series, barCount);
    }

    public static ROCIndicator roc(Indicator<Num> indicator, int barCount) {
        return new ROCIndicator(indicator, barCount);
    }

    public static RAVIIndicator rav(Indicator<Num> price, int shortSmaBarCount, int longSmaBarCount) {
        return new RAVIIndicator(price, shortSmaBarCount, longSmaBarCount);
    }

    public static PVOIndicator pvo(BarSeries series) {
        return new PVOIndicator(series);
    }

    public static PVOIndicator pvo(BarSeries series, int volumeBarCount) {
        return new PVOIndicator(series, volumeBarCount);
    }

    public static PVOIndicator pvo(BarSeries series, int shortBarCount, int longBarCount) {
        return new PVOIndicator(series, shortBarCount, longBarCount);
    }

    public static PVOIndicator pvo(BarSeries series, int volumeBarCount, int shortBarCount, int longBarCount) {
        return new PVOIndicator(series, volumeBarCount, shortBarCount, longBarCount);
    }

    public static PPOIndicator ppo(Indicator<Num> indicator) {
        return new PPOIndicator(indicator);
    }

    public static PPOIndicator ppo(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        return new PPOIndicator(indicator, shortBarCount, longBarCount);
    }

    public static ParabolicSarIndicator parabolicSar(BarSeries series) {
        return new ParabolicSarIndicator(series);
    }

    public static ParabolicSarIndicator parabolicSar(BarSeries series, Num aF, Num maxA) {
        return new ParabolicSarIndicator(series, aF, maxA);
    }

    public static ParabolicSarIndicator parabolicSar(BarSeries series, Num aF, Num maxA, Num increment) {
        return new ParabolicSarIndicator(series, aF, maxA, increment);
    }

    public static MMAIndicator mma(Indicator<Num> indicator, int barCount) {
        return new MMAIndicator(indicator, barCount);
    }

    public static MassIndexIndicator massIndex(BarSeries series, int emaBarCount, int barCount) {
        return new MassIndexIndicator(series, emaBarCount, barCount);
    }

    public static MACDIndicator macd(Indicator<Num> indicator) {
        return new MACDIndicator(indicator);
    }

    public static MACDIndicator macd(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        return new MACDIndicator(indicator, shortBarCount, longBarCount);
    }

    public static LWMAIndicator lwma(Indicator<Num> indicator, int barCount) {
        return new LWMAIndicator(indicator, barCount);
    }

    public static KSTIndicator kst(Indicator<Num> indicator) {
        return new KSTIndicator(indicator);
    }

    public static KSTIndicator kst(Indicator<Num> indicator, int rcma1SMABarCount, int rcma1ROCBarCount, int rcma2SMABarCount,
                                   int rcma2ROCBarCount, int rcma3SMABarCount, int rcma3ROCBarCount, int rcma4SMABarCount,
                                   int rcma4ROCBarCount) {
        return new KSTIndicator(indicator, rcma1SMABarCount, rcma1ROCBarCount, rcma2SMABarCount, rcma2ROCBarCount, rcma3SMABarCount, rcma3ROCBarCount, rcma4SMABarCount, rcma4ROCBarCount);
    }

    public static KAMAIndicator kama(Indicator<Num> price, int barCountEffectiveRatio, int barCountFast, int barCountSlow) {
        return new KAMAIndicator(price, barCountEffectiveRatio, barCountFast, barCountSlow);
    }

    public static KAMAIndicator kama(Indicator<Num> price) {
        return new KAMAIndicator(price);
    }

    public static HMAIndicator hma(Indicator<Num> indicator, int barCount) {
        return new HMAIndicator(indicator, barCount);
    }

    public static FisherIndicator fisher(Indicator<Num> price, int barCount) {
        return new FisherIndicator(price, barCount);
    }

    public static EMAIndicator ema(Indicator<Num> indicator, int barCount) {
        return new EMAIndicator(indicator, barCount);
    }

    public static DPOIndicator dpo(BarSeries series, int barCount) {
        return new DPOIndicator(series, barCount);
    }

    public static DPOIndicator dpo(Indicator<Num> price, int barCount) {
        return new DPOIndicator(price, barCount);
    }

    public static DoubleEMAIndicator doubleEma(Indicator<Num> indicator, int barCount) {
        return new DoubleEMAIndicator(indicator, barCount);
    }

    public static DistanceFromMAIndicator distanceFromMA(BarSeries series, Indicator<Num> movingAverage) {
        return new DistanceFromMAIndicator(series, movingAverage);
    }

    public static DateTimeIndicator date(BarSeries series) {
        return new DateTimeIndicator(series);
    }

    public static CoppockCurveIndicator coppockCurve(Indicator<Num> indicator) {
        return new CoppockCurveIndicator(indicator);
    }

    public static CoppockCurveIndicator coppockCurve(Indicator<Num> indicator, int longRoCBarCount, int shortRoCBarCount, int wmaBarCount) {
        return new CoppockCurveIndicator(indicator, longRoCBarCount, shortRoCBarCount, wmaBarCount);
    }

    public static CMOIndicator cmo(Indicator<Num> indicator, int barCount) {
        return new CMOIndicator(indicator, barCount);
    }

    public static ChopIndicator indicator(BarSeries barSeries, int ciTimeFrame, int scaleTo) {
        return new ChopIndicator(barSeries, ciTimeFrame, scaleTo);
    }

    public static ChandelierExitShortIndicator chandelierExitShort(BarSeries series) {
        return new ChandelierExitShortIndicator(series);
    }

    public static ChandelierExitShortIndicator chandelierExitShort(BarSeries series, int barCount, double k) {
        return new ChandelierExitShortIndicator(series, barCount, k);
    }

    public static ChandelierExitLongIndicator chandelierExitLong(BarSeries series) {
        return new ChandelierExitLongIndicator(series);
    }

    public static ChandelierExitLongIndicator chandelierExitLong(BarSeries series, int barCount, double k) {
        return new ChandelierExitLongIndicator(series, barCount, k);
    }

    public static CCIIndicator cci(BarSeries series, int barCount) {
        return new CCIIndicator(series, barCount);
    }

    public static AwesomeOscillatorIndicator ao(Indicator<Num> indicator, int barCountSma1, int barCountSma2) {
        return new AwesomeOscillatorIndicator(indicator, barCountSma1, barCountSma2);
    }

    public static AwesomeOscillatorIndicator ao(Indicator<Num> indicator) {
        return new AwesomeOscillatorIndicator(indicator);
    }

    public static AwesomeOscillatorIndicator ao(BarSeries series) {
        return new AwesomeOscillatorIndicator(series);
    }

    public static ATRIndicator atr(BarSeries series, int barCount) {
        return new ATRIndicator(series, barCount);
    }

    public static ATRIndicator atr(TRIndicator tr, int barCount) {
        return new ATRIndicator(tr, barCount);
    }

    public static AroonUpIndicator aroonUp(Indicator<Num> highPriceIndicator, int barCount) {
        return new AroonUpIndicator(highPriceIndicator, barCount);
    }

    public static AroonUpIndicator aroonUp(BarSeries series, int barCount) {
        return new AroonUpIndicator(series, barCount);
    }

    public static AroonOscillatorIndicator aroon(BarSeries series, int barCount) {
        return new AroonOscillatorIndicator(series, barCount);
    }

    public static AroonDownIndicator aroonDown(Indicator<Num> lowPriceIndicator, int barCount) {
        return new AroonDownIndicator(lowPriceIndicator, barCount);
    }

    public static AroonDownIndicator aroonDown(BarSeries series, int barCount) {
        return new AroonDownIndicator(series, barCount);
    }

    public static AccelerationDecelerationIndicator accelerationDeceleration(BarSeries series, int barCountSma1, int barCountSma2) {
        return new AccelerationDecelerationIndicator(series, barCountSma1, barCountSma2);
    }

    public static AccelerationDecelerationIndicator accelerationDeceleration(BarSeries series) {
        return new AccelerationDecelerationIndicator(series);
    }

    public static AccumulationDistributionIndicator accumulationDistribution(BarSeries series) {
        return new AccumulationDistributionIndicator(series);
    }

    public static ChaikinMoneyFlowIndicator chaikinMoneyFlow(BarSeries series, int barCount) {
        return new ChaikinMoneyFlowIndicator(series, barCount);
    }

    public static ChaikinOscillatorIndicator chaikinOscillator(BarSeries series, int shortBarCount, int longBarCount) {
        return new ChaikinOscillatorIndicator(series, shortBarCount, longBarCount);
    }

    public static ChaikinOscillatorIndicator chaikinOscillator(BarSeries series) {
        return new ChaikinOscillatorIndicator(series);
    }

    public static IIIIndicator iii(BarSeries series) {
        return new IIIIndicator(series);
    }

    public static MVWAPIndicator mvwap(VWAPIndicator vwap, int barCount) {
        return new MVWAPIndicator(vwap, barCount);
    }

    public static NVIIndicator nvi(BarSeries series) {
        return new NVIIndicator(series);
    }

    public static OnBalanceVolumeIndicator onBalanceVolume(BarSeries series) {
        return new OnBalanceVolumeIndicator(series);
    }

    public static PVIIndicator pvi(BarSeries series) {
        return new PVIIndicator(series);
    }

    public static ROCVIndicator rocv(BarSeries series, int barCount) {
        return new ROCVIndicator(series, barCount);
    }

    public static VWAPIndicator vwap(BarSeries series, int barCount) {
        return new VWAPIndicator(series, barCount);
    }

    public static CorrelationCoefficientIndicator correlationCoefficient(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        return new CorrelationCoefficientIndicator(indicator1, indicator2, barCount);
    }

    public static CovarianceIndicator covariance(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        return new CovarianceIndicator(indicator1, indicator2,barCount);
    }

    public static MeanDeviationIndicator meanDeviation(Indicator<Num> indicator, int barCount) {
        return new MeanDeviationIndicator(indicator, barCount);
    }

    public static PearsonCorrelationIndicator pearsonCorrelation(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        return new PearsonCorrelationIndicator(indicator1, indicator2, barCount);
    }

    public static PeriodicalGrowthRateIndicator periodicalGrowthRate(Indicator<Num> indicator, int barCount) {
        return new PeriodicalGrowthRateIndicator(indicator, barCount);
    }

    public static SigmaIndicator sigma(Indicator<Num> ref, int barCount) {
        return new SigmaIndicator(ref, barCount);
    }

    public static SimpleLinearRegressionIndicator simpleLinearRegression(Indicator<Num> indicator, int barCount) {
        return new SimpleLinearRegressionIndicator(indicator, barCount);
    }

    public static StandardDeviationIndicator standardDeviation(Indicator<Num> indicator, int barCount) {
        return new StandardDeviationIndicator(indicator, barCount);
    }

    public static StandardErrorIndicator standardError(Indicator<Num> indicator, int barCount) {
        return new StandardErrorIndicator(indicator, barCount);
    }

    public static VarianceIndicator variance(Indicator<Num> indicator, int barCount) {
        return new VarianceIndicator(indicator, barCount);
    }

    public static DeMarkPivotPointIndicator deMarkPivotPoint(BarSeries series, TimeLevel timeLevelId) {
        return new DeMarkPivotPointIndicator(series, timeLevelId);
    }

    public static DeMarkReversalIndicator deMarkReversal(DeMarkPivotPointIndicator pivotPointIndicator, DeMarkReversalIndicator.DeMarkPivotLevel level) {
        return new DeMarkReversalIndicator(pivotPointIndicator, level);
    }

    public static FibonacciReversalIndicator fibonacciReversal(PivotPointIndicator pivotPointIndicator, double fibonacciFactor,
                                                               FibonacciReversalIndicator.FibReversalTyp fibReversalTyp) {
        return new FibonacciReversalIndicator(pivotPointIndicator, fibonacciFactor, fibReversalTyp);
    }

    public static FibonacciReversalIndicator fibonacciReversal(PivotPointIndicator pivotPointIndicator, FibonacciReversalIndicator.FibonacciFactor fibonacciFactor,
                                                               FibonacciReversalIndicator.FibReversalTyp fibReversalTyp) {
        return new FibonacciReversalIndicator(pivotPointIndicator, fibonacciFactor, fibReversalTyp);
    }

    public static PivotPointIndicator pivotPoint(BarSeries series, TimeLevel timeLevel) {
        return new PivotPointIndicator(series, timeLevel);
    }

    public static StandardReversalIndicator standardReversal(PivotPointIndicator pivotPointIndicator, PivotLevel level) {
        return new StandardReversalIndicator(pivotPointIndicator, level);
    }

    public static KeltnerChannelLowerIndicator keltnerChannelLower(KeltnerChannelMiddleIndicator middle, double ratio, int barCountATR) {
        return new KeltnerChannelLowerIndicator(middle, ratio, barCountATR);
    }

    public static KeltnerChannelLowerIndicator keltnerChannelLower(KeltnerChannelMiddleIndicator middle, ATRIndicator atr, double ratio) {
        return new KeltnerChannelLowerIndicator(middle, atr, ratio);
    }

    public static KeltnerChannelMiddleIndicator keltnerChannelMiddle(BarSeries series, int barCountEMA) {
        return new KeltnerChannelMiddleIndicator(series, barCountEMA);
    }

    public static KeltnerChannelMiddleIndicator keltnerChannelMiddle(Indicator<Num> indicator, int barCountEMA) {
        return new KeltnerChannelMiddleIndicator(indicator, barCountEMA);
    }

    public static KeltnerChannelUpperIndicator keltnerChannelUpper(KeltnerChannelMiddleIndicator middle, double ratio, int barCountATR) {
        return new KeltnerChannelUpperIndicator(middle, ratio, barCountATR);
    }

    public static KeltnerChannelUpperIndicator keltnerChannelUpper(KeltnerChannelMiddleIndicator middle, ATRIndicator atr, double ratio) {
        return new KeltnerChannelUpperIndicator(middle, atr, ratio);
    }

    public static IchimokuChikouSpanIndicator ichimokuChikouSpan(BarSeries series) {
        return new IchimokuChikouSpanIndicator(series);
    }

    public static IchimokuChikouSpanIndicator ichimokuChikouSpan(BarSeries series, int timeDelay) {
        return new IchimokuChikouSpanIndicator(series, timeDelay);
    }

    public static IchimokuKijunSenIndicator ichimokuKijunSen(BarSeries series) {
        return new IchimokuKijunSenIndicator(series);
    }

    public static IchimokuKijunSenIndicator ichimokuKijunSen(BarSeries series, int barCount) {
        return new IchimokuKijunSenIndicator(series, barCount);
    }

    public static IchimokuLineIndicator ichimokuLine(BarSeries series, int barCount) {
        return new IchimokuKijunSenIndicator(series, barCount);
    }

    public static IchimokuSenkouSpanAIndicator ichimokuSenkouSpanA(BarSeries series) {
        return new IchimokuSenkouSpanAIndicator(series);
    }

    public static IchimokuSenkouSpanAIndicator ichimokuSenkouSpanA(BarSeries series, int barCountConversionLine, int barCountBaseLine) {
        return new IchimokuSenkouSpanAIndicator(series, barCountConversionLine, barCountBaseLine);
    }

    public static IchimokuSenkouSpanAIndicator ichimokuSenkouSpanA(BarSeries series, IchimokuTenkanSenIndicator conversionLine,
                                                                   IchimokuKijunSenIndicator baseLine, int offset) {
        return new IchimokuSenkouSpanAIndicator(series, conversionLine, baseLine, offset);
    }

    public static IchimokuSenkouSpanBIndicator ichimokuSenkouSpanB(BarSeries series) {
        return new IchimokuSenkouSpanBIndicator(series);
    }

    public static IchimokuSenkouSpanBIndicator ichimokuSenkouSpanB(BarSeries series, int barCount) {
        return new IchimokuSenkouSpanBIndicator(series, barCount);
    }

    public static IchimokuSenkouSpanBIndicator ichimokuSenkouSpanB(BarSeries series, int barCount, int offset) {
        return new IchimokuSenkouSpanBIndicator(series, barCount, offset);
    }

    public static IchimokuTenkanSenIndicator ichimokuTenkanSen(BarSeries series) {
        return new IchimokuTenkanSenIndicator(series);
    }

    public static IchimokuTenkanSenIndicator ichimokuTenkanSen(BarSeries series, int barCount) {
        return new IchimokuTenkanSenIndicator(series, barCount);
    }

    public static AmountIndicator amount(BarSeries series) {
        return new AmountIndicator(series);
    }

    public static BooleanTransformIndicator booleanTransform(Indicator<Num> indicator, Num coefficient, BooleanTransformIndicator.BooleanTransformType type) {
        return new BooleanTransformIndicator(indicator,coefficient, type);
    }

    public static BooleanTransformIndicator booleanTransform(Indicator<Num> indicator, BooleanTransformIndicator.BooleanTransformSimpleType type) {
        return new BooleanTransformIndicator(indicator, type);
    }

    public static CloseLocationValueIndicator closeLocation(BarSeries series) {
        return new CloseLocationValueIndicator(series);
    }

    public static CombineIndicator combine(Indicator<Num> indicatorLeft, Indicator<Num> indicatorRight,
                                           BinaryOperator<Num> combination) {
        return new CombineIndicator(indicatorLeft, indicatorRight, combination);
    }

    public static <T> ConstantIndicator<T> constant(BarSeries series, T t) {
        return new ConstantIndicator<>(series, t);
    }

    public static ConvergenceDivergenceIndicator convergenceDivergence(Indicator<Num> ref, Indicator<Num> other, int barCount,
                                                                       ConvergenceDivergenceIndicator.ConvergenceDivergenceType type, double minStrength, double minSlope) {
        return new ConvergenceDivergenceIndicator(ref, other, barCount, type, minStrength, minSlope);
    }

    public static ConvergenceDivergenceIndicator convergenceDivergence(Indicator<Num> ref, Indicator<Num> other, int barCount,
                                                                       ConvergenceDivergenceIndicator.ConvergenceDivergenceType type) {
        return new ConvergenceDivergenceIndicator(ref, other, barCount, type);
    }

    public static ConvergenceDivergenceIndicator convergenceDivergence(Indicator<Num> ref, Indicator<Num> other, int barCount,
                                                                       ConvergenceDivergenceIndicator.ConvergenceDivergenceStrictType strictType) {
        return new ConvergenceDivergenceIndicator(ref, other, barCount, strictType);
    }

    public static CrossIndicator cross(Indicator<Num> up, Indicator<Num> low) {
        return new CrossIndicator(up, low);
    }

    public static DifferencePercentageIndicator differencePercentage(Indicator<Num> indicator) {
        return new DifferencePercentageIndicator(indicator);
    }

    public static DifferencePercentageIndicator differencePercentage(Indicator<Num> indicator, Number percentageThreshold) {
        return new DifferencePercentageIndicator(indicator, percentageThreshold);
    }

    public static DifferencePercentageIndicator differencePercentage(Indicator<Num> indicator, Num percentageThreshold) {
        return new DifferencePercentageIndicator(indicator, percentageThreshold);
    }

    public static FixedBooleanIndicator fixedBoolean(BarSeries series, Boolean... values) {
        return new FixedBooleanIndicator(series, values);
    }

    public static FixedDecimalIndicator fixedDecimal(BarSeries series, double... values) {
        return new FixedDecimalIndicator(series, values);
    }

    public static FixedDecimalIndicator fixedDecimal(BarSeries series, String... values) {
        return new FixedDecimalIndicator(series, values);
    }

    public static <T> FixedIndicator fixed(BarSeries series, T... values) {
        return new FixedIndicator(series,values);
    }

    public static GainIndicator gain(Indicator<Num> indicator) {
        return new GainIndicator(indicator);
    }

    public static HighestValueIndicator highest(Indicator<Num> indicator, int barCount) {
        return new HighestValueIndicator(indicator, barCount);
    }

    public static LossIndicator loss(Indicator<Num> indicator) {
        return new LossIndicator(indicator);
    }

    public static LowestValueIndicator lowest(Indicator<Num> indicator, int barCount) {
        return new LowestValueIndicator(indicator, barCount);
    }

    public static MedianPriceIndicator median(BarSeries series) {
        return new MedianPriceIndicator(series);
    }

    public static PriceVariationIndicator price(BarSeries series) {
        return new PriceVariationIndicator(series);
    }

    public static PreviousValueIndicator previous(Indicator<Num> indicator) {
        return new PreviousValueIndicator(indicator);
    }

    public static PreviousValueIndicator previous(Indicator<Num> indicator, int n) {
        return new PreviousValueIndicator(indicator, n);
    }

    public static SumIndicator sum(Indicator<Num>... operands) {
        return new SumIndicator(operands);
    }

    public static TradeCountIndicator tradeCount(BarSeries series) {
        return new TradeCountIndicator(series);
    }

    public static TransformIndicator transform(Indicator<Num> indicator, UnaryOperator<Num> transformation) {
        return new TransformIndicator(indicator, transformation);
    }

    public static TRIndicator tr(BarSeries series) {
        return new TRIndicator(series);
    }

    public static TypicalPriceIndicator typicalPrice(BarSeries series) {
        return new TypicalPriceIndicator(series);
    }

    public static VolumeIndicator volume(BarSeries series) {
        return new VolumeIndicator(series);
    }

    public static VolumeIndicator volume(BarSeries series, int barCount) {
        return new VolumeIndicator(series, barCount);
    }

    public static BearishEngulfingIndicator bearishEngulfing(BarSeries series) {
        return new BearishEngulfingIndicator(series);
    }

    public static BearishHaramiIndicator bearishHarami(BarSeries series) {
        return new BearishHaramiIndicator(series);
    }

    public static BullishEngulfingIndicator bullishEngulfing(BarSeries series) {
        return new BullishEngulfingIndicator(series);
    }

    public static BullishHaramiIndicator bullishHarami(BarSeries series) {
        return new BullishHaramiIndicator(series);
    }

    public static DojiIndicator doji(BarSeries series, int barCount, double bodyFactor) {
        return new DojiIndicator(series, barCount,bodyFactor);
    }

    public static LowerShadowIndicator lowerShadow(BarSeries series) {
        return new LowerShadowIndicator(series);
    }

    public static RealBodyIndicator realBody(BarSeries series) {
        return new RealBodyIndicator(series);
    }

    public static ThreeBlackCrowsIndicator threeBlackCrows(BarSeries series, int barCount, double factor) {
        return new ThreeBlackCrowsIndicator(series, barCount, factor);
    }

    public static ThreeWhiteSoldiersIndicator threeWhiteSoldiers(BarSeries series, int barCount, Num factor) {
        return new ThreeWhiteSoldiersIndicator(series, barCount, factor);
    }

    public static UpperShadowIndicator upperShadow(BarSeries series) {
        return new UpperShadowIndicator(series);
    }

    public static BollingerBandsLowerIndicator bollingerBandsLower(BollingerBandsMiddleIndicator bbm, Indicator<Num> indicator) {
        return new BollingerBandsLowerIndicator(bbm, indicator);
    }

    public static BollingerBandsLowerIndicator bollingerBandsLower(BollingerBandsMiddleIndicator bbm, Indicator<Num> indicator, Num k) {
        return new BollingerBandsLowerIndicator(bbm, indicator, k);
    }

    public static BollingerBandsMiddleIndicator bollingerBandsMiddle(Indicator<Num> indicator) {
        return new BollingerBandsMiddleIndicator(indicator);
    }

    public static BollingerBandsUpperIndicator bollingerBandsUpper(BollingerBandsMiddleIndicator bbm, Indicator<Num> deviation) {
        return new BollingerBandsUpperIndicator(bbm, deviation);
    }

    public static BollingerBandsUpperIndicator bollingerBandsUpper(BollingerBandsMiddleIndicator bbm, Indicator<Num> deviation, Num k) {
        return new BollingerBandsUpperIndicator(bbm, deviation, k);
    }

    public static BollingerBandWidthIndicator bollingerBandWidth(BollingerBandsUpperIndicator bbu, BollingerBandsMiddleIndicator bbm,
                                                                 BollingerBandsLowerIndicator bbl) {
        return new BollingerBandWidthIndicator(bbu, bbm, bbl);
    }

    public static PercentBIndicator bollingerPercent(Indicator<Num> indicator, int barCount, double k) {
        return new PercentBIndicator(indicator, barCount,k);
    }

    public static ADXIndicator adx(BarSeries series, int diBarCount, int adxBarCount) {
        return new ADXIndicator(series, diBarCount, adxBarCount);
    }

    public static ADXIndicator adx(BarSeries series, int barCount) {
        return new ADXIndicator(series, barCount);
    }

    public static DXIndicator dx(BarSeries series, int barCount) {
        return new DXIndicator(series, barCount);
    }

    public static MinusDIIndicator minusDI(BarSeries series, int barCount) {
        return new MinusDIIndicator(series, barCount);
    }

    public static MinusDMIndicator minusDM(BarSeries series) {
        return new MinusDMIndicator(series);
    }

    public static PlusDIIndicator plusDI(BarSeries series, int barCount) {
        return new PlusDIIndicator(series, barCount);
    }

    public static PlusDMIndicator plusDM(BarSeries series) {
        return new PlusDMIndicator(series);
    }

    public static Returns returns(BarSeries barSeries, Position position, Returns.ReturnType type) {
        return new Returns(barSeries, position, type);
    }

    public static Returns returns(BarSeries barSeries, TradingRecord tradingRecord, Returns.ReturnType type) {
        return new Returns(barSeries, tradingRecord, type);
    }

    public static CashFlow cashFlow(BarSeries barSeries, Position position) {
        return new CashFlow(barSeries, position);
    }

    public static CashFlow cashFlow(BarSeries barSeries, TradingRecord tradingRecord) {
        return new CashFlow(barSeries, tradingRecord);
    }

    public static CashFlow cashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        return new CashFlow(barSeries, tradingRecord, finalIndex);
    }

    public static LinearBorrowingCostModel linearBorrowingCostModel(double feePerPeriod) {
        return new LinearBorrowingCostModel(feePerPeriod);
    }

    public static LinearTransactionCostModel linearTransactionCostModel(double feePerPeriod) {
        return new LinearTransactionCostModel(feePerPeriod);
    }

    public static ZeroCostModel zeroCostModel() {
        return new ZeroCostModel();
    }

    public static XorRule xor(Rule rule1, Rule rule2) {
        return new XorRule(rule1, rule2);
    }

    public static WaitForRule waitFor(Trade.TradeType tradeType, int numberOfBars) {
        return new WaitForRule(tradeType, numberOfBars);
    }

    public static UnderIndicatorRule under(Indicator<Num> indicator, Number threshold) {
        return new UnderIndicatorRule(indicator, threshold);
    }

    public static UnderIndicatorRule under(Indicator<Num> indicator, Num threshold) {
        return new UnderIndicatorRule(indicator, threshold);
    }

    public static UnderIndicatorRule under(Indicator<Num> first, Indicator<Num> second) {
        return new UnderIndicatorRule(first, second);
    }

    public static TrailingStopLossRule trailingStopLoss(Indicator<Num> indicator, Num lossPercentage, int barCount) {
        return new TrailingStopLossRule(indicator, lossPercentage, barCount);
    }

    public static TrailingStopLossRule trailingStopLoss(Indicator<Num> indicator, Num lossPercentage) {
        return new TrailingStopLossRule(indicator, lossPercentage);
    }

    public static TimeRangeRule timeRange(List<TimeRangeRule.TimeRange> timeRanges, DateTimeIndicator beginTimeIndicator) {
        return new TimeRangeRule(timeRanges, beginTimeIndicator);
    }

    public static StopLossRule stopLoss(ClosePriceIndicator closePrice, Number lossPercentage) {
        return new StopLossRule(closePrice, lossPercentage);
    }

    public static StopLossRule stopLoss(ClosePriceIndicator closePrice, Num lossPercentage) {
        return new StopLossRule(closePrice, lossPercentage);
    }

    public static StopGainRule stopGain(ClosePriceIndicator closePrice, Number gainPercentage) {
        return new StopGainRule(closePrice, gainPercentage);
    }

    public static StopGainRule stopGain(ClosePriceIndicator closePrice, Num gainPercentage) {
        return new StopGainRule(closePrice, gainPercentage);
    }

    public static OverIndicatorRule over(Indicator<Num> indicator, Number threshold) {
        return new OverIndicatorRule(indicator, threshold);
    }

    public static OverIndicatorRule over(Indicator<Num> indicator, Num threshold) {
        return new OverIndicatorRule(indicator, threshold);
    }

    public static OverIndicatorRule over(Indicator<Num> first, Indicator<Num> second) {
        return new OverIndicatorRule(first, second);
    }

    public static OrRule or(Rule rule1, Rule rule2) {
        return new OrRule(rule1, rule2);
    }

    public static OpenedPositionMinimumBarCountRule openedPositionMinimumBarCount(int barCount) {
        return new OpenedPositionMinimumBarCountRule(barCount);
    }

    public static NotRule not(Rule ruleToNegate) {
        return new NotRule(ruleToNegate);
    }

    public static JustOnceRule justOnce(Rule rule) {
        return new JustOnceRule(rule);
    }

    public static JustOnceRule justOnce() {
        return new JustOnceRule();
    }

    public static IsRisingRule isRising(Indicator<Num> ref, int barCount) {
        return new IsRisingRule(ref, barCount);
    }

    public static IsRisingRule isRising(Indicator<Num> ref, int barCount, double minStrenght) {
        return new IsRisingRule(ref, barCount, minStrenght);
    }
    
    public static IsLowestRule isLowest(Indicator<Num> ref, int barCount) {
        return new IsLowestRule(ref, barCount);
    }
    
    public static IsHighestRule isHighest(Indicator<Num> ref, int barCount) {
        return new IsHighestRule(ref, barCount);
    }
    
    public static IsFallingRule isFalling(Indicator<Num> ref, int barCount) {
        return new IsFallingRule(ref, barCount);
    }
    
    public static IsFallingRule isFalling(Indicator<Num> ref, int barCount, double minStrenght) {
        return new IsFallingRule(ref, barCount, minStrenght);
    }
    
    public static IsEqualRule isEqual(Indicator<Num> indicator, Number value) {
        return new IsEqualRule(indicator, value);
    }
    
    public static IsEqualRule isEqual(Indicator<Num> indicator, Num value) {
        return new IsEqualRule(indicator, value);
    }

    public static IsEqualRule isEqual(Indicator<Num> first, Indicator<Num> second) {
        return new IsEqualRule(first, second);
    }

    public static InSlopeRule inSlope(Indicator<Num> ref, Num minSlope) {
        return new InSlopeRule(ref, minSlope);
    }

    public static InSlopeRule inSlope(Indicator<Num> ref, Num minSlope, Num maxSlope) {
        return new InSlopeRule(ref, minSlope, maxSlope);
    }

    public static InSlopeRule inSlope(Indicator<Num> ref, int nthPrevious, Num maxSlope) {
        return new InSlopeRule(ref, nthPrevious, maxSlope);
    }

    public static InSlopeRule inSlope(Indicator<Num> ref, int nthPrevious, Num minSlope, Num maxSlope) {
        return new InSlopeRule(ref, nthPrevious, minSlope, maxSlope);
    }

    public static InPipeRule inPipe(Indicator<Num> ref, Number upper, Number lower) {
        return new InPipeRule(ref, upper, lower);
    }

    public static FixedRule fixed(int... indexes) {
        return new FixedRule(indexes);
    }

    public static DayOfWeekRule dayOfWeek(DateTimeIndicator timeIndicator, DayOfWeek... daysOfWeek) {
        return new DayOfWeekRule(timeIndicator, daysOfWeek);
    }

    public static CrossedUpIndicatorRule crossedUp(Indicator<Num> indicator, Number threshold) {
        return new CrossedUpIndicatorRule(indicator, threshold);
    }

    public static CrossedUpIndicatorRule crossedUp(Indicator<Num> indicator, Num threshold) {
        return new CrossedUpIndicatorRule(indicator, threshold);
    }

    public static CrossedUpIndicatorRule crossedUp(Indicator<Num> first, Indicator<Num> second) {
        return new CrossedUpIndicatorRule(first, second);
    }

    public static CrossedDownIndicatorRule crossedDown(Indicator<Num> indicator, Number threshold) {
        return new CrossedDownIndicatorRule(indicator, threshold);
    }

    public static CrossedDownIndicatorRule crossedDown(Indicator<Num> indicator, Num threshold) {
        return new CrossedDownIndicatorRule(indicator, threshold);
    }

    public static CrossedDownIndicatorRule crossedDown(Indicator<Num> first, Indicator<Num> second) {
        return new CrossedDownIndicatorRule(first, second);
    }

    public static ChainRule chain(Rule initialRule, ChainLink... chainLinks) {
        return new ChainRule(initialRule, chainLinks);
    }

    public static BooleanRule bool(boolean satisfied) {
        return new BooleanRule(satisfied);
    }

    public static BooleanIndicatorRule bool(Indicator<Boolean> indicator) {
        return new BooleanIndicatorRule(indicator);
    }

    public static AndRule and(Rule rule1, Rule rule2) {
        return new AndRule(rule1, rule2);
    }

    public static TradingStatementGenerator tradingStatementGenerator() {
        return new TradingStatementGenerator();
    }

    public static TradingStatementGenerator tradingStatementGenerator(PerformanceReportGenerator performanceReportGenerator,
                                                                      PositionStatsReportGenerator positionStatsReportGenerator) {
        return new TradingStatementGenerator(performanceReportGenerator, positionStatsReportGenerator);
    }

    public static TradingStatement tradingStatement(Strategy strategy, PositionStatsReport positionStatsReport,
                                                    PerformanceReport performanceReport) {
        return new TradingStatement(strategy, positionStatsReport, performanceReport);
    }

    public static PositionStatsReportGenerator positionStatsReportGenerator() {
        return new PositionStatsReportGenerator();
    }

    public static PositionStatsReport positionStatsReport(Num profitCount, Num lossCount, Num breakEvenCount) {
        return new PositionStatsReport(profitCount, lossCount, breakEvenCount);
    }

    public static PerformanceReportGenerator performanceReportGenerator() {
        return new PerformanceReportGenerator();
    }

    public static PerformanceReport performanceReport(Num totalProfitLoss, Num totalProfitLossPercentage, Num totalProfit, Num totalLoss) {
        return new PerformanceReport(totalProfitLoss, totalProfitLossPercentage, totalProfit, totalLoss);
    }
}
