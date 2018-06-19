package ta4jexamples.backtesting;

import org.ta4j.core.*;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class SimpleMovingAverageBacktesting {

    public static void main(String[] args) throws InterruptedException {
        TimeSeries series = createTimeSeries();

        Strategy strategy3DaySma = create3DaySmaStrategy(series);

        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecord3DaySma = seriesManager.run(strategy3DaySma, Order.OrderType.BUY, PrecisionNum.valueOf(50));
        System.out.println(tradingRecord3DaySma);

        Strategy strategy2DaySma = create2DaySmaStrategy(series);
        TradingRecord tradingRecord2DaySma = seriesManager.run(strategy2DaySma, Order.OrderType.BUY, PrecisionNum.valueOf(50));
        System.out.println(tradingRecord2DaySma);

        AnalysisCriterion criterion = new TotalProfitCriterion();
        Num calculate3DaySma = criterion.calculate(series, tradingRecord3DaySma);
        Num calculate2DaySma = criterion.calculate(series, tradingRecord2DaySma);

        System.out.println(calculate3DaySma);
        System.out.println(calculate2DaySma);
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

    private static Strategy create3DaySmaStrategy(TimeSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);
        return new BaseStrategy(
                new UnderIndicatorRule(sma, closePrice),
                new OverIndicatorRule(sma, closePrice)
        );
    }

    private static Strategy create2DaySmaStrategy(TimeSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 2);
        return new BaseStrategy(
                new UnderIndicatorRule(sma, closePrice),
                new OverIndicatorRule(sma, closePrice)
        );
    }
}
