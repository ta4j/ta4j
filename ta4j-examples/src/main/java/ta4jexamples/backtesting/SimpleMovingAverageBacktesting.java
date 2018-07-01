package ta4jexamples.backtesting;

import org.ta4j.core.*;
import org.ta4j.core.analysis.criteria.*;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class SimpleMovingAverageBacktesting {

    public static void main(String[] args) throws InterruptedException {
        TimeSeries series = createTimeSeries();

        Strategy strategy3DaySmaUnder = create3DaySmaUnderStrategy(series);

        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecord3DaySmaUnder = seriesManager.run(strategy3DaySmaUnder, Order.OrderType.BUY, PrecisionNum.valueOf(50));
        System.out.println(tradingRecord3DaySmaUnder);

        Strategy strategy3DaySmaOver = create3DaySmaOverStrategy(series);
        TradingRecord tradingRecord3DaySmaOver = seriesManager.run(strategy3DaySmaOver, Order.OrderType.BUY, PrecisionNum.valueOf(50));
        System.out.println(tradingRecord3DaySmaOver);

        calculateCriterion(new AverageProfitableTradesCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new AverageProfitCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new BuyAndHoldCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new LinearTransactionCostCriterion(5000, 0.005), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new MaximumDrawdownCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new NumberOfBarsCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new NumberOfTradesCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new RewardRiskRatioCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new TotalProfitCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
        calculateCriterion(new ProfitLossCriterion(), series, tradingRecord3DaySmaUnder, tradingRecord3DaySmaOver);
    }

    private static void calculateCriterion(AnalysisCriterion criterion, TimeSeries series, TradingRecord tradingRecord3DaySmaUnder, TradingRecord tradingRecord3DaySmaOver) {
        System.out.println("-- " + criterion.getClass().getSimpleName() + " --");
        Num calculate3DaySmaUnder = criterion.calculate(series, tradingRecord3DaySmaUnder);
        Num calculate3DaySmaOver = criterion.calculate(series, tradingRecord3DaySmaOver);
        System.out.println(calculate3DaySmaUnder);
        System.out.println(calculate3DaySmaOver);
        System.out.println();
    }

    private static TimeSeries createTimeSeries() {
        TimeSeries series = new BaseTimeSeries();
        series.addBar(new BaseBar(CreateDay(1), 100.0, 100.0, 100.0, 100.0, 1060, PrecisionNum::valueOf));
        series.addBar(new BaseBar(CreateDay(2), 110.0, 110.0, 110.0, 110.0, 1070, PrecisionNum::valueOf));
        series.addBar(new BaseBar(CreateDay(3), 140.0, 140.0, 140.0, 140.0, 1080, PrecisionNum::valueOf));
        series.addBar(new BaseBar(CreateDay(4), 119.0, 119.0, 119.0, 119.0, 1090, PrecisionNum::valueOf));
        series.addBar(new BaseBar(CreateDay(5), 100.0, 100.0, 100.0, 100.0, 1100, PrecisionNum::valueOf));
        series.addBar(new BaseBar(CreateDay(6), 110.0, 110.0, 110.0, 110.0, 1110, PrecisionNum::valueOf));
        series.addBar(new BaseBar(CreateDay(7), 120.0, 120.0, 120.0, 120.0, 1120, PrecisionNum::valueOf));
        series.addBar(new BaseBar(CreateDay(8), 130.0, 130.0, 130.0, 130.0, 1130, PrecisionNum::valueOf));
        return series;
    }

    private static ZonedDateTime CreateDay(int day) {
        return ZonedDateTime.of(2018, 01, day, 12, 0, 0, 0, ZoneId.systemDefault());
    }

    private static Strategy create3DaySmaUnderStrategy(TimeSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);
        return new BaseStrategy(
                new UnderIndicatorRule(sma, closePrice),
                new OverIndicatorRule(sma, closePrice)
        );
    }

    private static Strategy create3DaySmaOverStrategy(TimeSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);
        return new BaseStrategy(
                new OverIndicatorRule(sma, closePrice),
                new UnderIndicatorRule(sma, closePrice)
        );
    }
}
