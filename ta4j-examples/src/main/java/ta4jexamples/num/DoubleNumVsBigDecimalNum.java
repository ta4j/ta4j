package ta4jexamples.num;

import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Num.BigDecimalNum;
import org.ta4j.core.Num.DoubleNum;
import org.ta4j.core.Num.Num;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.ZonedDateTime;
import java.util.Scanner;

import static org.ta4j.core.Num.NaN.NaN;

public class DoubleNumVsBigDecimalNum {

    public static void main(String args[]){
        System.out.println("Use Double (D) or BigDecimal (B) for analysis?");
        String input = new Scanner(System.in).next().replace("\\s+","").toUpperCase();

        BaseTimeSeries.SeriesBuilder timeSeriesBuilder = new BaseTimeSeries.SeriesBuilder();
        timeSeriesBuilder.withName("Sample Series (10000)").withMaxBarCount(10000);

        if(input.equals("D")){
            System.out.println("Using Double for analysis...");
            timeSeriesBuilder.withNumTypeOf(DoubleNum::valueOf);
        } else{
            System.out.println("Using BigDecimal (default) for analysis...");
            timeSeriesBuilder.withNumTypeOf(BigDecimalNum::valueOf);
        }

        TimeSeries series = timeSeriesBuilder.build();

        for(int i = 9999; i >= 0; i--){
            ZonedDateTime date = ZonedDateTime.now().minusSeconds(i);
            series.addBar(date,i,i,i,i);
        }

        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        MACDIndicator macdIndicator = new MACDIndicator(closePriceIndicator);

        // do some calculations
        long start = System.currentTimeMillis();
        Num lastValue = NaN;
        for(int i = series.getBeginIndex(); i<= series.getEndIndex(); i++){
            lastValue = macdIndicator.getValue(i);
        }
        long end = System.currentTimeMillis();

        System.out.printf("Current value of %s: %s time: %s",macdIndicator.toString(),lastValue, (end-start));


    }
}
